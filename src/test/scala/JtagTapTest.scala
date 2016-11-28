// See LICENSE for license details.

package jtag.test

import Chisel.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import jtag._


class JtagTapTester(val c: JtagTap) extends PeekPokeTester(c) {

}

class JtagTapSpec extends ChiselFlatSpec {
  "JTAG TAP" should "work" in {
    Driver(() => new JtagTap()) {
      c => new JtagTapTester(c)
    } should be (true)
  }
}
