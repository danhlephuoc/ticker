import java.util.concurrent.TimeUnit

import common.Util._
import core._
import core.lars._
import engine.StreamEntry
import evaluation._

import scala.collection.immutable.HashMap
import scala.util.Random
import engine.ProgramLoader._

/**
  * Created by FM on 21.08.16.
  */
object BitEvaluation extends BitProgram {

  val all_001 = (0 to maxLevel) map (i => (level(IntValue(i)), 0.01)) toMap
  val all_01 = (0 to maxLevel) map (i => (level(IntValue(i)), 0.1)) toMap


  def main(args: Array[String]): Unit = {
    timings(args)

  }

  def timings(args: Array[String]): Unit = {
    // evaluate everything one time as pre-pre-warmup
    evaluateTimings(Seq("tms", "greedy") toArray)

    val dump = DumpData("Configuration", "Programs")
    val dumpToCsv = dump.printResults("bit-output.csv") _

    if (args.length == 0) {
      val allOptions = Seq(
        Seq("tms", "greedy"),
        //        Seq("tms", "doyle"),
        Seq("tms", "learn")
        //        Seq("clingo", "push")
      )

      val allResults = allOptions map (o => evaluateTimings(o.toArray))

      dump.plot(allResults)

      dumpToCsv(allResults)

    } else {
      val results = evaluateTimings(args)
      dump.plot(Seq(results))
      dumpToCsv(Seq(results))
    }
  }

  def evaluateTimings(args: Array[String], timePoints: Long = 500) = {

    val random = new Random(1)

    val evaluationOptions = Seq(
      ("0.01", all_001),
      ("0.25", all_01)
    )

    val program = groundLarsProgram()

    val evaluationCombination = evaluationOptions map { o =>

      val signals = generateSignals(o._2, random, 0, timePoints)

      (o._1, program, signals)
    }

    val option = args.mkString(" ")

    Console.out.println("Algorithm: " + option)

    AlgorithmResult(option, evaluationCombination map (c => executeTimings(args, c._1, c._2, c._3)) toList)
  }

  def generateSignals(probabilities: Map[Atom, Double], random: Random, t0: TimePoint, t1: TimePoint) = {
    val signals = (t0.value to t1.value) map (t => {
      val atoms = (probabilities filter (random.nextDouble() <= _._2) keys) toSet

      StreamEntry(TimePoint(t), atoms)
    })

    signals
  }

  def executeTimings(args: Array[String], instance: String, program: LarsProgram, signals: Seq[StreamEntry]) = {

    Console.out.println(f"Evaluating ${instance}")

    val provider = () => Evaluator.buildEngineFromArguments(args, program)

    val e = Evaluator(provider, 1, 2)

    val (append, evaluate) = e.streamInputsAsFastAsPossible(signals)

    TimingsConfigurationResult(instance, append, evaluate)
  }

  def failures(args: Array[String]): Unit = {
    val dump = DumpData("Configuration", "Instances")
    val dumpToCsv = dump.printResults("p18-failure-output.csv") _

    if (args.length == 0) {
      val allOptions = Seq(
        Seq("tms", "greedy"),
        //        Seq("tms", "doyle"),
        Seq("tms", "learn")
        //        Seq("clingo", "push")
      )

      val allResults = allOptions map (o => evaluateFailures(o.toArray))

      dump.plotFailures(allResults)

      //      dumpToCsv(allResults)

    } else {
      val results = evaluateFailures(args)
      dump.plotFailures(Seq(results))
      //      dumpToCsv(Seq(results))
    }
  }

  def evaluateFailures(args: Array[String], timePoints: Long = 1000) = {

    val random = new Random(1)

    val evaluationOptions = Map(
      ("0.01", all_001)
      //      ("0.25", all_025) -> Seq(P_4)
    )

    val program = groundLarsProgram()

    val evaluationCombination = evaluationOptions map { case (name, prop) =>

      val signals = generateSignals(prop, random, 0, timePoints)

      (name, program, signals)
    }

    val option = args.mkString(" ")

    Console.out.println("Algorithm: " + option)

    AlgorithmResult(option, evaluationCombination map (c => executeFailures(args, c._1, c._2, c._3)) toList)
  }

  def executeFailures(args: Array[String], instance: String, program: LarsProgram, signals: Seq[StreamEntry]) = {

    Console.out.println(f"Evaluating ${instance}")

    val provider = () => Evaluator.buildEngineFromArguments(args, program)

    val e = Evaluator(provider, 1, 2)

    val computations = e.successfulModelComputations(signals)

    SuccessConfigurationResult(instance, computations)
  }


}

trait BitProgram {

  val L = Variable("L")

  val bitEncodingRules = Seq[LarsRule](
    rule("bit(L,1) :- level(L), not bit(L,0)"),
    rule("bit(L,0) :- level(L), not bit(L,1)"),
    rule("sum_at(0,B) :- bit(0,B)"),
    rule("sum_at(L,C) :- sum_at(L0,C0), sum(L0,1,L), bit(L,1), pow(2,L,X), sum(C0,X,C), int(X), int(C)"),
    rule("sum_at(L,C) :- sum_at(L0,C), sum(L0,1,L), bit(L,0), int(C)"),
    rule("id(C) :- max_level(M), sum_at(M,C)"),
    rule("xx1 :- id(C), mod(C,10,K), geq(K,8), int(K), not xx1")
  )

  val highestExponent = 5
  //2^X; prepared program has 2^7
  val maxLevel = highestExponent - 1

  val levels = Seq(fact(f"max_level($maxLevel)")) ++ ((0 to maxLevel) map (i => fact(f"level($i)")))
  val ints = (0 to Math.pow(2, highestExponent).toInt) map (i => fact(f"int($i)"))

  val facts = levels ++ ints

  val signal = Atom("signal")
  val bit = Atom("bit")
  val level = Atom("level")
  val _1 = IntValue(1)

  val baseLarsProgram = LarsProgram(bitEncodingRules ++
    Seq[LarsRule](
      //      bit(L, _1) <= level(L) and W(20, Diamond, signal(L))
    )
    ++ facts
  )

  def groundLarsProgram() = {
    val grounder = Grounder(baseLarsProgram)

    LarsProgram(grounder.groundRules)
  }
}
