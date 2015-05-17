package ch.epfl.comparison

import breeze.linalg.{DenseMatrix, DenseVector}
import ch.epfl.structure._
import org.apache.spark.SparkContext.rddToPairRDDFunctions
import org.apache.spark.{SparkConf, SparkContext}


object Comparison {

  val alphabet = Vector("A", "B")

  /**
   * A spark job which finds all the pair of natural and synthetic
   * structures which are similar.
   *
   * Results are saved in a text file as a list of pair of natural structure id
   * and a list of similar synthetic structure ids: (String, List[String])
   *
   * FIXME: This job produces no results
   *
   * @param naturalsFile    File name for the natural structures
   * @param syntheticsFile  File name for the synthetic structures
   * @param outputFile      File name for output file
   */
  def findSimilar(naturalsFile: String, syntheticsFile: String, outputFile: String): Unit = {
    val conf = new SparkConf()
      .setAppName("Finding Similar Structures")
    val sc = new SparkContext(conf)

    val naturals = sc.textFile(naturalsFile)
      .flatMap(NaturalStructureParser.parse)
      .filter(_.nbElements <= 2)
      .map(normalize)
      .flatMap(renameSpecies)
      .map(s => (s.prettyFormula, s))

    val synthetics = sc.textFile(syntheticsFile)
      .flatMap(StructureParser.parse)
      .map(normalize)
      .flatMap(renameSpecies)
      .map(s => (s.prettyFormula, s))

    val similars = naturals.join(synthetics)
      .map(_._2)
      .filter { case (n, s) => Comparator.areSimilar(n, s) }
      .groupByKey()
      .collect { case (n, ss) if ss.nonEmpty => (n.id, ss.map(_.id).toList) }


    similars saveAsTextFile outputFile
  }

  /**
   * A spark job which finds all the similar synthetic structures.
   *
   * Results are saved in a text file as a list of pair of structure id
   * and a list of similar structure ids: (String, List[String])
   *
   * @param structuresFile  File name for the synthetic structures
   * @param outputFile      File name for output file
   */
  def findDuplicate(structuresFile: String, outputFile: String): Unit = {
    val conf = new SparkConf()
      .setAppName("Finding Duplicate Structures")
    val sc = new SparkContext(conf)

    val structures = sc.textFile(structuresFile)
      .flatMap(StructureParser.parse)
      .map(normalize)
      .flatMap(renameSpecies)
      .map(s => (s.prettyFormula, s))

    val duplicates = structures.join(structures)
      .map(_._2)
      .filter { case (s1, s2) => Comparator.areSimilar(s1, s2) }
      .groupByKey()
      .collect { case (n, ss) if ss.nonEmpty => (n.id, ss.map(_.id).toList) }

    duplicates saveAsTextFile outputFile
  }

  def renameSpecies(structure: Structure): List[Structure] = {
    require(structure.nbElements <= alphabet.length)
    val elems = alphabet take structure.nbElements
    val elemSet = elems.toSet

    structure.elements.toList.permutations.toList map { oldElems =>
      val substitutions = (oldElems.zipWithIndex map {
        case (e, i) => (e, alphabet(i))
      }).toMap

      val sites = structure.struct.sites map { site =>
        val newSpecies = site.species map { specie =>
          specie.copy(element = substitutions(specie.element))
        }
        site.copy(species = newSpecies)
      }

      val prettyFormula = (elems map { e =>
        val count = sites count (_.species.exists(_.element == e))
        if (count > 1) s"e$count"
        else if (count == 1) e
        else ""
      }).mkString

      structure.copy(elements = elemSet,
        struct = structure.struct.copy(sites = sites),
        prettyFormula = prettyFormula)
    }
  }

  def normalize(structure: Structure): Structure = {
    val Struct(sites, lattice) = structure.struct
    val factor = Math.cbrt(structure.nbSites / lattice.volume)

    val normLattice = normalizeLattice(lattice, factor)

    val normMatrix = DenseMatrix.tabulate(3, 3) { case (i, j) =>
      normLattice.matrix(i)(j)
    }

    val normSites = sites map { s =>
      val xyz = normMatrix * DenseVector(s.abc.toArray)
      s.copy(xyz = xyz.toArray.toList)
    }

    val normStruct = Struct(normSites, normLattice)
    structure.copy(struct = normStruct)
  }

  private def normalizeLattice(lattice: Lattice, factor: Double): Lattice = {
    val a = lattice.a * factor
    val b = lattice.b * factor
    val c = lattice.c * factor
    val matrix = lattice.matrix map (_ map (_ * factor))

    lattice.copy(a = a, b = b, c = c, matrix = matrix, volume = a * b * c)
  }
}
