package jtms.asp.examples

import clingo.{ClingoEvaluation}
import core.Evaluation
import jtms.evaluation.ExtendedJTMSEvaluation
import org.scalatest.FlatSpec

/**
  * Created by FM on 25.02.16.
  */
trait EvaluateASPImplementations {
  this: FlatSpec =>

  val asp = ClingoEvaluation()
  val answerUpdate = new ExtendedJTMSEvaluation

  def theSame(tests: => Evaluation => Unit) = {
    "The ASP implementation" should behave like tests(asp)
    "The AnswerUpdate implementation" should behave like tests(answerUpdate)
  }
}
