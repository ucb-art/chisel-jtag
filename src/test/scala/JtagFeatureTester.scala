// See LICENSE for license details.

package jtag.test

import Chisel.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import chisel3._
import jtag._

class JtagIdcodeTester(val c: Module{val io: JtagBlockIO}) extends PeekPokeTester(c) with JtagTestUtilities {
  import BinaryParse._

  val jtag = c.io.jtag
  val output = c.io.output

  resetToIdle()
  idleToIRShift()
  irShift("00", "10")
  irShiftToIdle()

  idleToDRShift()
  drShift("00000000000000000000000000000000", "1010 0000000100100011 00001000010 1".reverse)
  drShiftToIdle()
}

class JtagIdcodeModule(irLength: Int, idcode: (BigInt, BigInt)) extends Module {
  val controller = JtagTapGenerator(irLength, Map(), idcode=Some(idcode))
  val io = IO(new JtagBlockIO(irLength)) //controller.io.cloneType
  io.jtag <> controller.io.jtag
  io.output := controller.io.output
}

class JtagTapSpec extends ChiselFlatSpec {
  "JTAG TAP should output a proper IDCODE" should "work" in {
    //Driver(() => new JtagTap(2)) {  // multiclock doesn't work here yet
    Driver(() => JtagReclocked(() => new JtagIdcodeModule(2, (0, JtagIdcode(0xA, 0x123, 0x42)))), backendType="verilator") {
      c => new JtagIdcodeTester(c)
    } should be (true)
  }
}
