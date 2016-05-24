package lars.time

import fixtures.TimeTestFixtures
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

/**
  * Created by FM on 16.05.16.
  */
class TimeVariableSpec extends FlatSpec with TimeTestFixtures {

  "A TimeVariable T" should "be converted to 'T'" in {
    T.toString should be("T")
  }

  it should "be grounded to t1" in {
    T.ground(t1) should be(t1)
  }

  "Adding 1 to TimeVariable T" should "lead to 'T + 1'" in {
    (T + 1).toString should be("T + 1")
  }
  it should "be grounded to t2" in {
    (T + 1).ground(t1) should be(t2)
  }

  "Subtracting 1 from TimeVariable T" should "lead to 'T - 1'" in {
    (T - 1).toString should be("T - 1")
  }
  it should "be grounded to t0" in {
    (T - 1).ground(t1) should be(t0)
  }
}
