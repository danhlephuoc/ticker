package engine

import scala.collection.SortedMap

/**
  * Created by FM on 08.04.16.
  */
class OrderedAtomStream {
  var inputStream = SortedMap.empty[Time, Set[Atom]](
    Ordering.fromLessThan((l, r) => l.milliseconds < r.milliseconds)
  )

  def append(time: Time)(atoms: Set[Atom]): Unit = {
    val previousValue = inputStream.getOrElse(time, Set[Atom]())
    inputStream = inputStream.updated(time, previousValue ++ atoms)
  }

  def evaluate(time: Time): Set[Atom] = {
    inputStream.getOrElse(time, Set())
  }

}
