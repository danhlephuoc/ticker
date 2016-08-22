package core.lars

import core.Atom

/**
  * Created by FM on 01.05.16.
  */
object Format {

  def apply(windowFunction: WindowFunction) = windowFunction match {
    case SlidingTimeWindow(windowSize) => f"⊞^$windowSize"
    case SlidingTupleWindow(windowSize)=> f"⊞_#^$windowSize"
    case FluentWindow=>f"⊞^f"
  }

  def apply(temporalOperator: TemporalModality) = temporalOperator match {
    case Diamond => "◇"
    case Box => "☐"
    case At(time) => f"@_$time"
  }

  def apply(atom: WindowAtom): String = {
    val parts = Seq(
      apply(atom.windowFunction),
      apply(atom.temporalModality),
      atom.atom.predicate
    )
    parts mkString " "
  }

  def apply(atom: ExtendedAtom): String = atom match {
    case w: WindowAtom => apply(w)
    case a: Atom => a.predicate.toString
  }

  def apply(atom: HeadAtom): String = atom match {
    case a: Atom => a.predicate.toString
    case at: AtAtom => apply(At(at.time)) + " " + at.atom.predicate
  }

  def apply(rule: LarsRule): String = {
    f"${apply(rule.head)} :- ${rule.pos map apply mkString ", "}${rule.neg map apply mkString(", not ", ", not ", "")}. "
  }

  def apply(program: LarsProgram): Seq[String] = {
    program.rules map apply
  }

}
