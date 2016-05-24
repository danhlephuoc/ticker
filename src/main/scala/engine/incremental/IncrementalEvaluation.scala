package engine.incremental

import core.Atom
import core.asp.{AspFact, PlainAspProgram}
import core.lars.TimePoint
import engine.{Result, _}
import jtms.ExtendedJTMS

/**
  * Created by FM on 05.04.16.
  */
case class IncrementalEvaluation(private val program: PlainAspProgram) extends EvaluationEngine {
  val intensionalAtomStream: OrderedAtomStream = new OrderedAtomStream

  val answerUpdateNetwork = ExtendedJTMS(program)

  def append(time: TimePoint)(atoms: Atom*): Unit = {
    intensionalAtomStream.append(time)(atoms.toSet)
  }

  def evaluate(time: TimePoint) = {
    val facts = intensionalAtomStream.evaluate(time).map(x => AspFact(x))
    facts foreach answerUpdateNetwork.add

    new Result {
      override def get(): Option[Set[Atom]] = {
        answerUpdateNetwork.getModel()
      }
    }
  }
}

