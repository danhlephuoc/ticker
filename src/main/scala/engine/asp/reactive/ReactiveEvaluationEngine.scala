package engine.asp.reactive

import clingo.reactive.{ReactiveClingo, ReactiveClingoProgram, ClingoTick}
import clingo.{ClingoEvaluation, ClingoWrapper}
import common.Resource
import core._
import core.lars.TimePoint
import engine._
import engine.asp._

/**
  * Created by FM on 18.05.16.
  */
case class ReactiveEvaluationEngine(program: LarsProgramEncoding, clingoWrapper: ClingoWrapper = ClingoWrapper()) extends EvaluationEngine with Resource {

  val clingoProgram: ReactiveClingoProgram = ReactiveClingoProgram.fromMapped(program)
  val reactiveClingo = new ReactiveClingo(clingoWrapper)

  val runningReactiveClingo = reactiveClingo.executeProgram(clingoProgram)

  val signalTracker = SignalTracker(program.maximumTimeWindowSizeInTicks, program.maximumTupleWindowSize, (g, t, p) => TrackedSignalForClingo(g, t, p))

  def close() = runningReactiveClingo.close

  override def append(time: TimePoint)(atoms: Atom*): Unit = {

    val groundAtoms = trackAtoms(time, atoms)

    //TODO hb: I commented this to save time
    //runningReactiveClingo.signal(groundAtoms map (_.clingoArgument))
  }

  override def evaluate(time: TimePoint): Result = {

    val parameters = Seq(
      ClingoTick(clingoProgram.timeDimension.parameter, time.value),
      ClingoTick(clingoProgram.countDimension.parameter, signalTracker.tupleCount)
    )

    val clingoModel = runningReactiveClingo.evaluate(parameters)

    discardOutdatedAtoms(time)

    clingoModel match {
      case Some(model) => {
        //TODO add filtering for such that clingoModel contains only output stream
        val models: Set[Model] = model.map(_.map(ClingoEvaluation.convert))


        Result(Some(models.head.collect {
          case p: PinnedAtAtom if p.time == time => p.atom
        })) //pick first
      }
      case None => NoResult
    }
  }

  private def discardOutdatedAtoms(time: TimePoint) = {
    val signalsToRemove = signalTracker.discardOutdatedSignals(time)
    //TODO hb I commented this to save time
    //runningReactiveClingo.expire(signalsToRemove.map(_.clingoArgument))
  }

  private def trackAtoms(time: TimePoint, atoms: Seq[Atom]): Seq[TrackedSignal] = {
    signalTracker.track(time, atoms)
  }

  //TODO naming: clingo or reactive clingo?
  case class TrackedSignalForClingo(signal: GroundAtom, time: TimePoint, count: Long) extends TrackedSignal {
    val timeDimension = ClingoTick(clingoProgram.timeDimension.parameter, time.value)
    val cntDimension = ClingoTick(clingoProgram.countDimension.parameter, count)
    val clingoArgument = (signal, Seq(timeDimension, cntDimension))
  }

}
