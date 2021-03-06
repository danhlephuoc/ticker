package processing

import core._
import core.asp.{AspFact, AspProgram, AspRule}
import fixtures.AtomTestFixture
import reasoner.incremental.jtms.asp.examples.SingleHusbandSample
import org.scalatest.FlatSpec
import reasoner.asp.clingo.ClingoCall

/**
  * Created by FM on 25.02.16.
  */
class AspSpec extends FlatSpec with AtomTestFixture{

  "An empty program" should "be executed and converted back to empty models" in {
    val program = AspProgram()

    val asp = ClingoCall(program)
    assert(asp.isEmpty)
  }

  "A program containing only a premise A" should "be executed an converted to one single model containing A" in {
    val program = AspProgram(AspFact(a))

    val asp = ClingoCall(program)

    assert(asp.nonEmpty)
    assert(asp contains Set(a))
  }

  "A program containing a premise and a rule" should "return only the premise" in {
    val program = AspProgram(AspFact(a), AspRule.pos(b).head(c))

    val asp = ClingoCall(program)

    assert(asp.nonEmpty)
    assert(asp.size == 1)
    assert(asp contains Set(a))
  }
  it should "return two nodes" in {
    val program = AspProgram(AspFact(a), AspRule.pos(a).head(b))

    val asp = ClingoCall(program)

    assert(asp.nonEmpty)
    assert(asp.size == 1)
    assert(asp contains Set(a, b))
  }

  "A program with two models" can "be executed and converted back into both models" in {
    val example = new SingleHusbandSample()
    val asp = ClingoCall(example.pHusbandFirst)

    assert(asp.size == 2)
    assert(asp contains Set(example.man, example.husband))
    assert(asp contains Set(example.man, example.single))
  }

  "A program with rules with arity 1 and 2" should "be executed by ASP" in {
    val bb: Atom = b("1")
    val cc: Atom = c("1", "2")

    val program = AspProgram(
      a :- bb,
      bb :- cc,
      cc
    )

    val asp = ClingoCall(program)

    assert(asp.head.size == 3)
    assert(asp.head == Set(cc, bb, a))
  }

  "Models with arity" should "be parsed into AtomsWithArguments" in {
    val cc: Atom = c("a", "b")

    assert(NonGroundAtom(c.predicate, List("a", "b")) == ClingoCall.convert("c(a,b)"))
  }
}