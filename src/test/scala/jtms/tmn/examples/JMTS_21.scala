package jtms.tmn.examples

import core.{Rule, ContradictionAtom}

/**
  * Created by FM on 11.02.16.
  */
class JMTS_21 extends JTMSSpec {
  val N_cont = ContradictionAtom("N_cont")

  val j7 = Rule.in(B).out(C).head(N_cont)

  def JTMS_DDB = {
    val tmn = JTMS

    tmn.N +=  N_cont

    tmn.add(j7)

    tmn
  }

  "The model" should "contain A,C,D,F,E" in {
    assert(JTMS_DDB.getModel() == Set(A, C, D, F, E))
  }
}