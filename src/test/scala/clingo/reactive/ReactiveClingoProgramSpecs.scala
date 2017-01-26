package clingo.reactive

import clingo.{ClingoSignalAtom, ReactiveClingoProgram, TickParameter}
import core.Predicate
import org.scalatest.FlatSpec
import org.scalatest.Inspectors._
import org.scalatest.Matchers._

/**
  * Created by fm on 26/01/2017.
  */
class ReactiveClingoProgramSpecs extends FlatSpec {

  val emptyProgram = ReactiveClingoProgram(Set(), Set())

  "A program without signals or volatile rules" should "have no #external signals" in {
    assert(emptyProgram.signalPrograms.isEmpty)
  }

  it should "have only external constraints" in {
    pending
    val externalLines = emptyProgram.program.lines.
      filter(_.startsWith("#external")).
      toSeq

    forExactly(1, externalLines) { p => assert(p.contains("now(t)")) }
    forExactly(1, externalLines) { p => assert(p.contains("cnt(c)")) }

    assert(externalLines.size == 2)
  }

  it should "contain no lines without #" in {
    val lines = emptyProgram.program.lines.filterNot(_.trim.isEmpty).toSeq
    forAll(lines) { line => line should startWith("#") }
  }

  "A program with a signal" should "have one external entry" in {
    val s = ReactiveClingoProgram(Set(), Set(ClingoSignalAtom(Predicate("b"))))

    assert(s.program.contains("#external at_b(t)."))
    assert(s.program.contains("#external cnt_b(c)."))
  }
  it should "have a program named signals_b" in {
    val s = ReactiveClingoProgram(Set(), Set(ClingoSignalAtom(Predicate("b"))))

    assert(s.program.contains("#program signals_b_0"))
  }

  "A program with a signal with arity 1" should "have a program with additional parameters" in {
    val s = ReactiveClingoProgram(Set(), Set(ClingoSignalAtom(Predicate("b"), Seq(TickParameter("b_arg")))))

    assert(s.program.contains("#program signals_b_1"))
    assert(s.program.contains(", b_arg)"))
  }

  "A program with a volatile rule" should "have one rule entry" in {
    val s = ReactiveClingoProgram(Set("a :- b."), Set())

    assert(s.program.contains("a :- b."))
  }

  "A program with a rule and a signal" should "have both entries" in {
    val s = ReactiveClingoProgram(Set("a :- b."), Set(ClingoSignalAtom(Predicate("b"))))

    assert(s.program.contains("at_b(t)"))
    assert(s.program.contains("a :- b"))
  }
}
