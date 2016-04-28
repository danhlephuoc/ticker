package engine

import core.{Atom, Program, not}
import engine.implementations.{AspEvaluation, AspPullEvaluation}
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import org.scalatest.OptionValues._

/**
  * Created by FM on 21.04.16.
  */
class EngineStreamSpec extends FlatSpec {

  val a = Atom("a")
  val b = Atom("b")
  val c = Atom("c")
  val d = Atom("d")

  val program = Program(
    a :- b,
    b :- c and not(d)
  )

  val t1 = At.second(1)
  val t2 = At.second(2)
  val t3 = At.second(3)

  def evaluationEngine: EvaluationEngine = AspEvaluation.pull(program)

  "Adding atoms one after another at the same timepoint" should "lead to different evaluation results" in {
    val engine = evaluationEngine

    val atT1 = engine.append(t1) _

    atT1(Seq(Atom("c")))

    assume(Set(a, b, c(t1.toString)) subsetOf engine.evaluate(t1).get.value)

    atT1(Seq(Atom("d")))

    engine.evaluate(t1).get.value should contain allOf(c(t1.toString), d(t1.toString))
  }

  "Adding one atom at t2" should "not lead to a result at t3" in {
    val engine = evaluationEngine

    engine.append(t2)(Atom("c"))

    assume(Set(a, b, c).subsetOf(engine.evaluate(t2).get.value))

    pendingUntilFixed {
      assert(engine.evaluate(t3).get.isEmpty)
    }
  }

  it should "not lead to a result when evaluating at t1" in {
    val engine = evaluationEngine

    engine.append(t2)(Atom("c"))

    assume(Set(a, b, c) subsetOf engine.evaluate(t2).get.value)

    pendingUntilFixed {
      assert(engine.evaluate(t1).get.isEmpty)
    }
  }
}