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
  val io = IO(controller.cloneType)
  io <> controller
}

class JtagRegisterTester(val c: JtagClocked[JtagRegisterModule]) extends JtagTester(c) {
  import BinaryParse._

  // Necessary to get cross-clock-domain registers into reset state
  poke(c.reset, 1)
  poke(c.io.jtag.TCK, 0)
  step(1)
  poke(c.io.jtag.TCK, 1)
  step(1)
  poke(c.reset, 0)
  step(1)

  resetToIdle()
  expect(c.io.reg, 42)  // reset sanity check
  idleToIRShift()
  irShift("0001".reverse, "??01".reverse)
  irShiftToIdle()

  idleToDRShift()
  expect(c.io.reg, 42)
  drShift("00101111", "00101010".reverse)
  drShiftToIdle()
  expect(c.io.reg, 0xF4)
  idleToDRShift()
  drShift("11111111", "00101111")
  drShiftToIdle()
  expect(c.io.reg, 0xFF)
  idleToDRShift()
  drShift("00000000", "11111111")
  drShiftToIdle()
  expect(c.io.reg, 0x00)
}

  class ModuleIO(irLength: Int) extends JtagBlockIO(irLength) {
    val reg = Output(UInt(8.W))

    override def cloneType = new ModuleIO(irLength).asInstanceOf[this.type]
  }

class JtagRegisterModule() extends JtagModule {
  val irLength = 4

  val reg = Reg(UInt(8.W), init=42.U)

  val chain = Module(new CaptureUpdateChain(8))
  chain.io.capture.bits := reg
  when (chain.io.update.valid) {
    reg := chain.io.update.bits
  }

  val controller = JtagTapGenerator(irLength, Map(chain -> 1))


  val io = IO(new ModuleIO(irLength))
  io.jtag <> controller.jtag
  io.output <> controller.output
  io.reg := reg
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
