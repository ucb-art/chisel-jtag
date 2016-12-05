// See LICENSE for license details.

package jtag.test

import Chisel.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import chisel3._
import jtag._

class JtagIdcodeTester(val c: JtagTapModule) extends PeekPokeTester(c) with JtagTestUtilities {
  import BinaryParse._

  val jtag = c.io.jtag
  val output = c.io.output
  val status = c.io.status

  resetToIdle()
  idleToIRShift()
  irShift("00", "10")
  irShiftToIdle()

  idleToDRShift()
  drShift("00000000000000000000000000000000", "00000000000000000000000000000001".reverse)
  drShiftToIdle()
}

class JtagTapSpec extends ChiselFlatSpec {
  "JTAG TAP should output a proper IDCODE" should "work" in {
    def idcodeJtagGenerator(irLength: Int): JtagTapController = {
      JtagTapGenerator(irLength, Map(), idcode=Some((0, 1)))
    }

    //Driver(() => new JtagTap(2)) {  // multiclock doesn't work here yet
    Driver(() => new JtagTapModule(2, idcodeJtagGenerator), backendType="verilator") {
      c => new JtagIdcodeTester(c)
    } should be (true)
  }
}
