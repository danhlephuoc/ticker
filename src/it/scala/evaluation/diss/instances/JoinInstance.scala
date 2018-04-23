package evaluation.diss.instances

import core.{Atom, Model}
import evaluation.diss.Helpers._
import evaluation.diss.Instance
import evaluation.diss.PreparedAtoms.{a, string2Atom}
import evaluation.diss.instances.AnalyticCommon._
import evaluation.diss.programs.JoinProgramProvider
import reasoner.Result

/**
  * Created by hb on 20.04.18.
  *
  * wm: window and modality indicator: {ta,td,tb,ca,cd,cb}
  * scale: nr of atoms for guards of form g(X)
  */
case class JoinInstance(wm: String, windowSize: Int, signalEvery: Int, scale: Int) extends Instance with JoinProgramProvider{

  assert(signalEvery > 0)
  assert(scale > 0)

  val winMod = winModFromString(wm)

  var lastB:Boolean = false
  var n:Int = 0
  def generateSignalsToAddAt(t: Int): Seq[Atom] = {
    if (t % signalEvery == 0) {
      if (n >= scale) {
        n = 1
      }
      val pred:String = {
        if (lastB) {
          lastB = false
          "c"
        } else {
          lastB = true
          "b"
        }
      }
      n = n + 2
      val atom:Atom = pred+"("+(n-2)+","+(n-1)+")"
      Seq[Atom](atom)
    } else {
      Seq()
    }
  }

  def verifyOutput(result: Result, t: Int): Unit = {
    val model = result.model
    winMod match {
      case `time_at` => verify_time_at(model,t)
      case `time_diamond` => verify_time_diamond(model,t)
      case `time_box` => verify_time_box(model,t)
      case `count_at` => verify_count_at(model,t)
      case `count_diamond` => verify_count_diamond(model,t)
      case `count_box` => verify_count_box(model,t)
    }
  }

  private def verify_time_at(model: Model, t: Int): Unit = {
    verify_time_diamond(model,t)
  }

  private def verify_time_diamond(model: Model, t: Int): Unit = {
    if (t % signalEvery <= windowSize) {
      mustHave(model,a,t)
    } else {
      mustNotHave(model,a,t)
    }
  }

  private def verify_time_box(model: Model, t: Int): Unit = {
    if (t == 0) {
      mustHave(model,a,t)
    } else if (signalEvery == 1) {
      mustHave(model,a,t)
    } else {
      mustNotHave(model,a,t)
    }
  }

  private def verify_count_at(model: Model, t: Int): Unit = {
    verify_count_diamond(model,t)
  }

  private def verify_count_diamond(model: Model, t: Int): Unit = {
    mustHave(model,a,t)
  }

  private def verify_count_box(model: Model, t: Int): Unit = {
    if (t == 0) {
      mustHave(model,a,t)
    } else if (signalEvery == 1) {
      mustHave(model,a,t)
    } else {
      mustNotHave(model,a,t)
    }
  }



}
