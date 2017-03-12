package engine.asp

import core._
import core.asp.{AspRule, NormalRule}
import core.lars._

import scala.concurrent.duration._

/**
  * Created by fm on 20/01/2017.
  */
case class PlainLarsToAspMapper(engineTimeUnit: EngineTimeUnit = 1 second) extends LarsToAspMapper {

  def identityRulesForAtom(a: Atom): Seq[NormalRule] = {
    Seq(
      AspRule[Atom, Atom](a, Set(now(TimePinVariable), PinnedAtom.asPinnedAtAtom(a, TimePinVariable))),
      AspRule[Atom, Atom](PinnedAtom.asPinnedAtAtom(a, TimePinVariable), Set(now(TimePinVariable), a))
    )
  }

  def encodingAtom(extendedAtom: ExtendedAtom): Atom = extendedAtom match {
    case AtAtom(t, a) => PinnedAtom.asPinnedAtAtom(a, t)
    case a: Atom => a
    case a: WindowAtom => this.encodedWindowAtom(a)
  }

  // windowAtom: \window^1 @_T a(X)
  // head: w_{bla}(X,T)
  def slidingTime(window: SlidingTimeWindow, windowAtom: WindowAtom): WindowAtomEncoder = {
    val length = timePoints(window.windowSize.unit, window.windowSize.length)
    val head = encodedWindowAtom(windowAtom) //TODO beautify
    windowAtom.temporalModality match {
      case a: At => TimeAtEncoder(length, windowAtom.atom, head.asInstanceOf[PinnedAtAtom], a.time)
      case Diamond => TimeDiamondEncoder(length, windowAtom.atom, head)
      case Box => TimeBoxEncoder(length, windowAtom.atom, head)
    }
  }

  def slidingTuple(window: SlidingTupleWindow, windowAtom: WindowAtom): WindowAtomEncoder = {
    val head = encodedWindowAtom(windowAtom) //TODO beautify
    windowAtom.temporalModality match {
      case a: At => TupleAtEncoder(window.windowSize, windowAtom.atom, head.asInstanceOf[PinnedAtAtom], a.time)
      case Diamond => TupleDiamondEncoder(window.windowSize, windowAtom.atom, head)
      case Box => TupleBoxEncoder(window.windowSize, windowAtom.atom, head)
    }
  }

  def encodedWindowAtom(windowAtom: WindowAtom) = {
    val predicate = predicateFor(windowAtom)
    val previousArguments = windowAtom.atom match {
      case aa: AtomWithArguments => aa.arguments
      case a: Atom => Seq()
    }

    windowAtom.temporalModality match {
      case At(v: Time) => PinnedAtom.asPinnedAtAtom(Atom(predicate, previousArguments), v) //note the slight difference in semantics: if this pinned atom holds, then the atom within holds at the respective time, not the window atom itself
      case _ => Atom(predicate, previousArguments)
    }
  }

  def timePoints(unit: TimeUnit, size: Long) = Duration(unit.toMillis(size) / engineTimeUnit.toMillis, engineTimeUnit.unit).length
}


object PlainLarsToAspMapper {
  def asNormalRule(rule: Rule[Atom, Atom]): NormalRule = AspRule(rule.head, rule.pos, rule.neg)

  def asNormalRules(rule: Rule[Atom, Atom]): Seq[NormalRule] = Seq(asNormalRule(rule))

  def asNormalRules(rules: Seq[Rule[Atom, Atom]]): Seq[NormalRule] = rules map asNormalRule
}

/*
   at this point we have a representation for multiple evaluation modes:
   - for one-shot/reactive solving, everything is there by ruleEncodings plus the allWindowRules in windowAtomEncoders
   - for incremental solving, we use ruleEncodings + incrementalRulesAt (at every time point)

   b <- \window^1 \Diamond a

   b <- w           <-- this rule is contained in ruleEncodings
   w <- a(t-1)      <-- these rules are contained in allWindowRules, since they have a window atom representation in their head
   w <- a(t-0)
 */

case class TimeAtEncoder(length: Long, atom: Atom, windowAtomEncoding: PinnedAtAtom, atTime: Time) extends TimeWindowEncoder {

  val allWindowRules = (0 to length.toInt) map { i =>
    val timePos: Time = atTime match {
      case TimePoint(t) => t - i
      case _ => TimePinVariable - i
    }
    val head: PinnedAtAtom = windowAtomEncoding.resetPin(timePos)
    val b: Atom = PinnedAtom.asPinnedAtAtom(atom, timePos)
    AspRule[Atom,Atom](head, Set(now(TimePinVariable), b))
  }

  def incrementalRules(tick: Tick): Seq[(Expiration,NormalRule)] = {
    val t = tick.time
    val head:Atom = windowAtomEncoding.resetPin(t)
    val posBody = Set(PinnedAtom.asPinnedAtAtom(atom,t))
    val rule: NormalRule = AspRule(head,posBody)
    val exp: Expiration = Tick(t + length + 1, Void)
    Seq((exp,rule))
  }

  override def ticksUntilWindowAtomIsOutdated(): TicksUntilOutdated = Tick(length+1,Void)

}

/* EXAMPLE.
   b <- \window^range \Diamond a.
   ==>
   b <- w_{range-d-a}
   w_{range-d-a} <- now(N), a_at(T), T=N-0 //...range

   atom: Atom ... a
   windowAtomEncoding: w_{range-d-a}
 */
case class TimeDiamondEncoder(length: Long, atom: Atom, windowAtomEncoding: Atom) extends TimeWindowEncoder {

  val allWindowRules = (0 to length.toInt) map { i =>
    val b: Atom = PinnedAtom.asPinnedAtAtom(atom, TimePinVariable - i)
    AspRule[Atom, Atom](windowAtomEncoding, Set[Atom](now(TimePinVariable), b))
  }

  override def incrementalRules(tick: Tick): Seq[(Expiration,NormalRule)] = {
    val t = tick.time
    val posBody = Set[Atom](PinnedAtom.asPinnedAtAtom(atom,t))
    val rule: NormalRule = AspRule(windowAtomEncoding,posBody)
    val exp: Expiration = Tick(t + length + 1, Void)
    Seq((exp,rule))
  }

  override def ticksUntilWindowAtomIsOutdated(): TicksUntilOutdated = Tick(Void,Void) //since time variable not included

}

case class TimeBoxEncoder(length: Long, atom: Atom, windowAtomEncoding: Atom) extends TimeWindowEncoder {

  val spoilerAtom = Atom(Predicate(f"spoil_te_${length}_${atom.predicate.caption}"), Atom.unapply(atom).getOrElse(Seq()))

  val baseRule: NormalRule = AspRule(windowAtomEncoding, Set(atom), Set(spoilerAtom))

  val spoilerRules: Seq[NormalRule] = (1 to length.toInt) map  { i =>
    AspRule(spoilerAtom, Set[Atom](atom, now(TimePinVariable)), Set[Atom](PinnedAtom.asPinnedAtAtom(atom, TimePinVariable - i)))
  }

  override val allWindowRules: Seq[NormalRule] = spoilerRules :+ baseRule

  override def incrementalRules(tick: Tick): Seq[(Expiration,NormalRule)] = {
    val baseRule: NormalRule = AspRule(windowAtomEncoding,Set(atom),Set(spoilerAtom))
    val expBase: Expiration = Tick(Void,Void)
    if (length == 0) return Seq((expBase,baseRule))

    val t = tick.time - 1
    val spoilerRule: NormalRule = AspRule(spoilerAtom, Set(atom), Set(PinnedAtom.asPinnedAtAtom(atom, TimePoint(t))))
    val expSp: Expiration = Tick(tick.time + length, Void)
    Seq((expBase,baseRule),(expSp,spoilerRule))
  }

  override def ticksUntilWindowAtomIsOutdated(): TicksUntilOutdated = Tick(Void,Void) //since time variable not included
}

case class TupleAtEncoder(length: Long, atom: Atom, windowAtomEncoding: PinnedAtAtom, atTime: Time) extends TupleWindowEncoder {
  val D = Variable("DD")

  // at atoms got their parameter already encoded
  val allWindowRules = (0 to length.toInt) map { i =>
    AspRule[Atom, Atom](windowAtomEncoding, Set(cnt(CountPinVariable), PinnedAtom.asPinnedAtCntAtom(atom, atTime, D), Sum(CountPinVariable, IntValue(-i), D)))
  }

  override def incrementalRules(tick: Tick): Seq[(Expiration,NormalRule)] = {
    val t = TimePoint(tick.time)
    val c = tick.count.toInt
    val head:Atom = windowAtomEncoding.resetPin(t)
    val posBody = Set(PinnedAtom.asPinnedAtCntAtom(atom,t,IntValue(c)))
    val rule: NormalRule = AspRule(head,posBody)
    val exp: Expiration = Tick(Void, c + length)
    Seq((exp,rule))
  }

  override def ticksUntilWindowAtomIsOutdated(): TicksUntilOutdated = Tick(Void, length)
}

case class TupleDiamondEncoder(length: Long, atom: Atom, windowAtomEncoding: Atom) extends TupleWindowEncoder {

  val T = TimeVariableWithOffset(StringVariable("TT"))

  val allWindowRules = (0 to length.toInt) map { i =>
    AspRule(windowAtomEncoding, Set(cnt(CountPinVariable), PinnedAtom.asPinnedAtCntAtom(atom, T, CountPinVariable - i)))
  }

  override def incrementalRules(tick: Tick): Seq[(Expiration,NormalRule)] = {
    val t = tick.time
    val c = tick.count.toInt
    val posBody = Set(PinnedAtom.asPinnedAtCntAtom(atom,TimePoint(t),IntValue(c)))
    val rule: NormalRule = AspRule(windowAtomEncoding,posBody)
    val exp: Expiration = Tick(Void, c + length)
    Seq((exp,rule))
  }

  override def ticksUntilWindowAtomIsOutdated(): TicksUntilOutdated = Tick(Void,Void) //no time/count variable in window atom
}

case class TupleBoxEncoder(length: Long, atom: Atom, windowAtomEncoding: Atom) extends TupleWindowEncoder {

  val baseRule: NormalRule = AspRule(windowAtomEncoding, Set(atom), Set(spoilerAtom))

  val T = TimeVariableWithOffset("TT")
  val U = TimeVariableWithOffset("UU")

  val spoilerAtom = Atom(Predicate(f"spoil_tu_${length}_${atom.predicate.caption}"), Atom.unapply(atom).getOrElse(Seq()))
  val startAtom = Atom(Predicate(f"start_tu_${length}_${atom.predicate.caption}"), Seq(T))

  val spoilerRule: NormalRule = AspRule(spoilerAtom,
    Set[Atom](atom,startAtom,now(TimePinVariable),LeqLt(T,U,TimePinVariable)),
    Set[Atom](PinnedAtom.asPinnedAtAtom(atom, U)))

  val startRule: NormalRule = AspRule(spoilerAtom,
    Set[Atom](
      atom,
      cnt(CountPinVariable),
      tickAtom(T,CountPinVariable - length.toInt + 1)
    ))

  val spoilingRules: Seq[NormalRule] = Seq(spoilerRule,startRule)

  override val allWindowRules: Seq[NormalRule] = spoilingRules :+ baseRule

  //this variable may go if we can remove predicate "tick" from the pos body of the incremental rule
  //requires more intelligent grounding
  val D = TimeVariableWithOffset(StringVariable("DD"))

  override def incrementalRules(tick: Tick): Seq[(Expiration,NormalRule)] = {
    val t = TimePoint(tick.time)

    val baseRule: NormalRule = AspRule(windowAtomEncoding,Set(atom),Set(spoilerAtom))
    val expBase: Expiration = Tick(Void,Void)
    if (length < 2) return Seq((expBase,baseRule))

    val spoilerRule: NormalRule = AspRule(spoilerAtom,
      Set[Atom](atom,startAtom,LeqLt(T,U,t),tickAtom(U,D)),
      Set[Atom](PinnedAtom.asPinnedAtAtom(atom, U)))

    val startRule: NormalRule = AspRule(startAtom,
      Set[Atom](
        atom,
        tickAtom(T,IntValue(Math.max(0,tick.count.toInt - length.toInt + 1)))
      ))

    val expSpoiler: Expiration = Tick(tick.time + 1, Void)
    val expStart: Expiration = Tick(Void, tick.count + 1)
    Seq((expBase,baseRule),(expSpoiler,spoilerRule),(expStart,startRule))
  }

  override def ticksUntilWindowAtomIsOutdated(): TicksUntilOutdated = Tick(Void, Void) //no time/count variable in window atom
}

