package jtms

import core._
import core.asp.{NormalRule, NormalProgram}

import scala.collection.immutable.{HashSet, HashMap}
import scala.util.Random

object JtmsLearn {

  def apply(P: NormalProgram): JtmsLearn = {
    val net = new JtmsLearn()
    P.rules foreach net.add
    net
  }

}

/**
  * Refinement of JtmsGreedy that learns to avoid bad choices.
  *
  */
class JtmsLearn(override val random: Random = new Random()) extends JtmsGreedy {

  override def updateGreedy(atoms: Set[Atom]) {
    atoms foreach setUnknown
    //test avoidance map before determining further consequences
    selectNextAtom()
    if (selectedAtom.isEmpty) {
      atomsNeedingSupp() foreach setUnknown
    } else {
      saveState
    }
    while (hasUnknown) {
      unknownAtoms foreach findStatus
      selectNextAtom()
      selectedAtom match {
        case Some(atom) => {
          chooseStatusGreedy(atom)
          saveState
        }
        case None => if (hasUnknown) throw new IncrementalUpdateFailureException()
      }
    }
    //reset for next update iteration
    resetSavedState
  }

  case class PartialState( support: Map[Atom, Long], stateHash:Long) {
//  case class PartialState(status: Map[Atom, Status], support: Map[Atom, Set[Atom]]) {
    override def toString: String = {
      val sb = new StringBuilder
      sb.append("State[").append("\n\t\tstatus: ").append(status).append("\n\t\tsupport: ").append(support).append("]")
      sb.toString
    }

    private lazy val precomputedHash = scala.runtime.ScalaRunTime._hashCode(PartialState.this)

    override def hashCode(): Int = precomputedHash

    //    override lazy val hashCode(): Int = scala.runtime.ScalaRunTime._hashCode(UserDefinedAspRule.this)
  }

  def saveState() {
    prevState = state
    prevSelectedAtom = selectedAtom
  }

  def resetSavedState(): Unit = {
    prevState = None
    prevSelectedAtom = None
  }

  var selectedAtom: Option[Atom] = None
  var prevSelectedAtom: Option[Atom] = None

  // state braucht nicht zwingend alle objekte als referenz. muss nur eindeutig sein
  // -> dafür vllt. bitset oder sowas?
  var state: Option[PartialState] = None
  var prevState: Option[PartialState] = None

  case class PrecomputedHashCodeOfHashSet(rules: HashSet[NormalRule], incrementalHash: Long = IncrementalHashCode.emptyHash) {

    def contains(rule: NormalRule) = rules.contains(rule)

    def + (rule:NormalRule) = PrecomputedHashCodeOfHashSet(rules + rule, IncrementalHashCode.addHashCode(incrementalHash,rule))
    def - (rule:NormalRule) = PrecomputedHashCodeOfHashSet(rules - rule, IncrementalHashCode.removeHashCode(incrementalHash,rule))

//    private val precomputedHash = scala.runtime.ScalaRunTime._hashCode(PrecomputedHashCodeOfHashSet.this)
    private val precomputedHash =incrementalHash.hashCode()

    override def hashCode(): Int = precomputedHash

  }

  class AllRulesTabu() {

    var ruleMap: Map[PrecomputedHashCodeOfHashSet, CurrentRulesTabu] = Map.empty.withDefaultValue(new CurrentRulesTabu())

    var stateRules: PrecomputedHashCodeOfHashSet = PrecomputedHashCodeOfHashSet(HashSet())
    var currentRulesTabu = new CurrentRulesTabu()

    def add(rule: NormalRule): Unit = {
      if (!stateRules.contains(rule)) {
        stateRules = stateRules + rule
        updateAfterRuleChange()
      }
    }

    def remove(rule: NormalRule): Unit = {
      if (stateRules.contains(rule)) {
        stateRules = stateRules - rule
        updateAfterRuleChange()
      }
    }

    def updateAfterRuleChange(): Unit = {
      // TODO: perf: contains takes very long???
      if (ruleMap.contains(stateRules)) {
        currentRulesTabu = ruleMap(stateRules)
      } else {
        currentRulesTabu = new CurrentRulesTabu()
        // TODO: perf: updated.computeHash takes very long???
        ruleMap = ruleMap.updated(stateRules, currentRulesTabu)
      }
    }

    def avoid(state: PartialState, atomToAvoid: Atom): Unit = {
      currentRulesTabu.save(state, atomToAvoid)
    }

    def atomsToAvoid(): Set[Atom] = {
      currentRulesTabu.atomsToAvoid()
    }

    override def toString(): String = {
      val sb = new StringBuilder()
      for ((ruleSet, tb) <- ruleMap) {
        sb.append("rules: ").append(ruleSet)
        for ((state, atoms) <- tb.avoidanceMap) {
          sb.append("\n\tin ").append(state)
          sb.append("\n\tavoid ").append(atoms)
        }
        sb.append("\n")
      }
      sb.toString
    }
  }

  class CurrentRulesTabu() {
    var avoidanceMap = new HashMap[PartialState, Set[Atom]]

    def save(state: PartialState, atomToAvoid: Atom): Unit = {
      val set: Set[Atom] = avoidanceMap.getOrElse(state, Set()) + atomToAvoid
      avoidanceMap = avoidanceMap.updated(state, set)
    }

    def atomsToAvoid() = avoidanceMap.getOrElse(state.get, Set[Atom]())
  }

  val tabu = new AllRulesTabu()


  override def register(rule: NormalRule): Boolean = {
    val newRule = super.register(rule)
    if (!newRule) {
      return false
    } else if (dataIndependentRule(rule)) {
      tabu.add(rule)
    }
    true
  }

  override def unregister(rule: NormalRule): Boolean = {
    val ruleExisted = super.unregister(rule)
    if (!ruleExisted) {
      return false
    } else if (dataIndependentRule(rule)) {
      tabu.remove(rule)
    }
    true
  }

  //note that in this case, invalidateModel is not supposed to be called from outside!
  override def invalidateModel(): Unit = {

    if (selectedAtom.isDefined) {
      tabu.avoid(state.get, selectedAtom.get)
      //updateAvoidanceMap(this.state.get,selectedAtom.get)
    }

    super.invalidateModel()
  }

  def stateSnapshot(): Option[PartialState] = {
    //    val filteredStatus = status filter { case (atom,status) => isStateAtom(atom) }
    val currentStateAtoms = stateAtoms
//    val filteredStatus = status filterKeys currentStateAtoms.contains
    // TODO: perf: isStateAtom as dict-lookup?
    //    val collectedSupp = supp collect { case (atom,set) if isStateAtom(atom) => (atom,set filter (!extensional(_))) }
//    val collectedSupp = supp filterKeys currentStateAtoms.contains collect { case (atom, set) => (atom, set diff extensionalAtoms) }
    val collectedSupp = __suppHash filterKeys currentStateAtoms.contains

//     val recomputedSupp =  supp filterKeys currentStateAtoms.contains collect { case (atom, set) => (atom,IncrementalHashCode.hash(set diff extensionalAtoms)) }


    Some(PartialState( collectedSupp, __stateHash))
  }

  def stateAtoms = (inAtoms union outAtoms) diff extensionalAtoms

  //skip facts! - for asp they are irrelevant, for tms they change based on time - no stable basis
  def isStateAtom(a: Atom): Boolean = (status(a) == in || status(a) == out) && !extensional(a)

  def selectNextAtom(): Unit = {

    state = stateSnapshot()

    //    val atomSet = (unknownAtoms filter (!extensional(_)))
    val atomSet = unknownAtoms diff extensionalAtoms
    val atoms = if (shuffle && atomSet.size > 1) (random.shuffle(atomSet.toSeq)) else atomSet

    if (atoms.isEmpty) return

    // TODO: perf: find iterates over to many atoms - dict?
    val tabuAtoms = tabu.atomsToAvoid()
    selectedAtom = atoms find (!tabuAtoms.contains(_))
    //    selectedAtom = atoms find (!tabu.atomsToAvoid().contains(_))

    if (selectedAtom.isEmpty && prevState.isDefined) {
      tabu.avoid(prevState.get, prevSelectedAtom.get)
    }

    /*
    if (doForceChoiceOrder) {
      val maybeAtom: Option[Atom] = forcedChoiceSeq find (status(_) == unknown)
      if (maybeAtom.isDefined) {
        selectedAtom = maybeAtom
        return
      }
    }
    */

    /*
    val javaList = new java.util.ArrayList[Atom]()
    for (a <- atoms) {
      javaList.add(a)
    }
    if (shuffle) {
      java.util.Collections.shuffle(javaList)
    }

    val iterator: util.Iterator[Atom] = javaList.iterator()

    var elem: Atom = iterator.next()

    val atomsToAvoid = avoidanceMap.getOrElse(state.get,scala.collection.immutable.Set())

    while ((atomsToAvoid contains elem) && iterator.hasNext) {
      elem = iterator.next()
    }

    if (atomsToAvoid contains elem) {
      if (prevState.isDefined) {
        updateAvoidanceMap(prevState.get, prevSelectedAtom.get)
      }
      selectedAtom = None
    } else {
      selectedAtom = Some(elem)
    }

    */

  }

}