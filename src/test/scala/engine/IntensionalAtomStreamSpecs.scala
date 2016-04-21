package engine

import core.Atom
import org.scalatest.FlatSpec

/**
  * Created by FM on 08.04.16.
  */
class IntensionalAtomStreamSpecs extends FlatSpec {

  val atom = Atom("a")

  val t0 = Time(0)
  val t1 = Time(1)
  val t2 = Time(2)

  def defaultEngine = {
    new OrderedAtomStream
  }

  "An empty engine" should "not evaluate to a result at t0" in {
    val engine = defaultEngine
    assert(engine.evaluate(t0) == Set())
  }

  "Appending atom a at t1" should "allow it to be queried at t1" in {
    val engine = defaultEngine

    engine.append(t1)(Set(atom))

    assert(engine.evaluate(t1) == Set(atom))
  }

  it should "not be queried at t0" in {
    val engine = defaultEngine


    engine.append(t1)(Set(atom))

    assert(engine.evaluate(t0) == Set())
  }

  it should "not be available at t2" in {
    val engine = defaultEngine

    engine.append(t1)(Set(atom))

    assert(engine.evaluate(t2) == Set())
  }

  "Adding to atoms after each other" should "result in only atom at t1" in {
    val engine = defaultEngine

    engine.append(t0)(Set(atom))

    val atom2 = Atom("b")

    engine.append(t1)(Set(atom2))

    assert(engine.evaluate(t1) == Set(atom2))
  }

  "Adding two atoms at the same time point" should "allow both to be queried" in {
    val engine = defaultEngine
    val atT1 = engine.append(t1) _

    atT1(Set(atom))

    val atom2 = Atom("b")

    atT1(Set(atom2))

    assert(engine.evaluate(t1) == Set(atom, atom2))
  }

}
