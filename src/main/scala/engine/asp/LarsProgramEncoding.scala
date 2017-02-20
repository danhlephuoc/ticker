package engine.asp

import core.{Atom, IntValue}
import core.asp.{AspFact, NormalProgram, NormalRule}
import core.lars.{EngineTimeUnit, LarsBasedProgram, LarsRule}

/**
  * Created by fm on 20/02/2017.
  */
//to derive window atom encoding
trait WindowAtomEncoder {
  val length: Long

  val allWindowRules: Seq[NormalRule] //one-shot/reactive clingo solving: e.g. for window^3 diamond all 4 rules

  def incrementalRulesAt(tick: IntValue): IncrementalRules
}


trait TimeWindowEncoder extends WindowAtomEncoder

trait TupleWindowEncoder extends WindowAtomEncoder

case class LarsRuleEncoding(larsRule: LarsRule, ruleEncodings: Set[NormalRule], windowAtomEncoders: Set[WindowAtomEncoder])

case class LarsProgramEncoding(larsRuleEncodings: Seq[LarsRuleEncoding], nowAndAtNowIdentityRules: Seq[NormalRule], backgroundData: Set[Atom]) extends NormalProgram with LarsBasedProgram {

  val baseRules = (larsRuleEncodings flatMap (_.ruleEncodings)) ++ nowAndAtNowIdentityRules ++ (backgroundData map (AspFact(_))) //for one-shot solving

  val windowAtomEncoders = larsRuleEncodings flatMap (_.windowAtomEncoders)

  val oneShotWindowRules = windowAtomEncoders flatMap (_.allWindowRules)

  // full representation of Lars-Program as asp
  override val rules = baseRules ++ oneShotWindowRules

  override val larsRules = larsRuleEncodings map (_.larsRule)

  val maximumTimeWindowSizeInTicks: Long = larsRuleEncodings.
    flatMap(_.windowAtomEncoders).
    collect {
      case t: TimeWindowEncoder => t.length
    } match {
    case Nil => 0
    case x => x.max
  }
}

case class IncrementalRules(toAdd: Seq[NormalRule], toRemove: Seq[NormalRule])

