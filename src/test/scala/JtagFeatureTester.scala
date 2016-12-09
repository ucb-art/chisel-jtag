// See LICENSE for license details.

package jtag.test

import Chisel.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import chisel3._
import jtag._

class JtagIdcodeTester(val c: JtagClocked[JtagIdcodeModule]) extends JtagTester(c) {
  import BinaryParse._

  resetToIdle()
  idleToIRShift()
  irShift("00", "10")
  irShiftToIdle()

  idleToDRShift()
  drShift("00000000000000000000000000000000", "1010 0000000100100011 00001000010 1".reverse)
  drShiftToIdle()
}

class JtagIdcodeModule(irLength: Int, idcode: (BigInt, BigInt)) extends JtagModule {
  val controller = JtagTapGenerator(irLength, Map(), idcode=Some(idcode))
  val io = IO(new JtagBlockIO(irLength)) //controller.io.cloneType
  io.jtag <> controller.io.jtag
  io.output := controller.io.output
}

class JtagRegisterTester(val c: JtagClocked[JtagRegisterModule]) extends JtagTester(c) {
  import BinaryParse._

  resetToIdle()
  idleToIRShift()
  irShift("00", "10")
  irShiftToIdle()

  idleToDRShift()
}

class JtagRegisterModule() extends JtagModule {
  val controller = JtagTapGenerator(2, Map())
  val io = IO(new JtagBlockIO(2)) //controller.io.cloneType
  io.jtag <> controller.io.jtag
  io.output := controller.io.output
}

class JtagTapSpec extends ChiselFlatSpec {
  "JTAG TAP should output a proper IDCODE" should "work" in {
    //Driver(() => new JtagTap(2)) {  // multiclock doesn't work here yet
    Driver(() => JtagClocked(() => new JtagIdcodeModule(2, (0, JtagIdcode(0xA, 0x123, 0x42)))), backendType="verilator") {
      c => new JtagIdcodeTester(c)
    } should be (true)
  }
  "JTAG data registers" should "capture and update" in {
    //Driver(() => new JtagTap(2)) {  // multiclock doesn't work here yet
    Driver(() => JtagClocked(() => new JtagRegisterModule()), backendType="verilator") {
      c => new JtagRegisterTester(c)
    } should be (true)
  }
}
