package jtms.evaluation

import core.Evaluation
import core.asp.NormalProgram
import jtms.JtmsBeierle

/**
  * Created by FM on 25.02.16.
  */
class JTMNBeierleEvaluation extends Evaluation {

  def apply(program: NormalProgram) = {
    val tmn = JtmsBeierle(program)

    val singleModel = tmn.getModel.get

    Set(singleModel)
  }

}
