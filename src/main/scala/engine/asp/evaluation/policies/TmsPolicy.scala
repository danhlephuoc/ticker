package engine.asp.evaluation.policies

import core._
import core.lars.TimePoint
import engine.asp.evaluation.{GroundRule, GroundedNormalRule}

/**
  * Created by FM on 12.06.16.
  */
trait TmsPolicy {
  def initialize(groundRules: Seq[GroundRule])

  def add(timePoint: TimePoint)(rules: Seq[GroundRule])

  def getModel(timePoint: TimePoint): Option[Model]

  def remove(timePoint: TimePoint)(rules: Seq[GroundRule])
}
