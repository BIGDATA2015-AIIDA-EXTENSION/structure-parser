package ch.epfl.clustering
import scala.collection.mutable.{HashMap => MHashMap}

/**
 * Created by lukas on 07/05/15.
 */
object Clustering {
  def cluster[T](elems: List[T], distance: (T, T) => Double, nbCluster: Int): ClusteredStructure[T] = {

    val elemInCluster = MHashMap[T, Int]()
    val clusters = MHashMap[Int, Clust]()
    val nbElems = elems.size
    val nbDist = nbElems*(nbElems-1)/2
    var distances = new Array[DistHolder](nbDist)

    case class Clust(id: Int, elems: List[T]) {

      def fusionCluster(cl: Clust): Unit = {
        this.elems ++= cl.elems
      }

    }
    case class DistHolder(e1: T, e2: T, distance: Double) {
      def sameCluster(): Boolean = {
        val e1Id = elemInCluster.get(e1).get
        val e2Id = elemInCluster.get(e2).get
        e1Id == e2Id
      }

      def getElem1Cluster: Clust = {
        clusters.get(elemInCluster.get(e1).get).get
      }

      def getElem2Cluster: Clust = {
        clusters.get(elemInCluster.get(e2).get).get
      }
    }

    def clusterize(): List[Clust] = {
      var nbIter = nbElems - nbCluster
      var index = 0

      while(nbIter > 0) {
        while(distances(index).sameCluster()) index +=1
        val cl1 = distances(index).getElem1Cluster
        val cl2 = distances(index).getElem2Cluster

        cl1.fusionCluster(cl2)

        cl2.elems.foreach(e => elemInCluster.put(e, cl1.id))
        clusters.remove(cl2.id)

        index +=1
        nbIter -=1
      }
      clusters.values.toList
    }

    var index = 0
    for (i <- 0 until nbElems) {
      elemInCluster.put(elems(i), i)
      clusters.put(i, Clust(i, elems(i) :: Nil))
      for (j <- i+1 until nbElems) {
        val e1 = elems(i)
        val e2 = elems(j)
        distances(index) = DistHolder(e1, e2, distance(e1, e2))
        index +=1
      }
    }

    distances = distances.sortBy(_.distance)

    ClusteredStructure(clusterize().map(cl => Cluster(cl.elems)))
  }
}


case class ClusteredStructure[T](clusters: List[Cluster[T]])

case class Cluster[T](elems: List[T])
