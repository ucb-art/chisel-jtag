// See LICENSE for license details.

package jtag.test

import Chisel.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import chisel3._
import chisel3.util._
import jtag._

class NegativeEdgeLatchBoolTest(val c: NegativeEdgeLatchTestModule[Bool]) extends PeekPokeTester(c) {
  poke(c.io.clock, 1)  // reset to high
  poke(c.io.enable, 1)

  poke(c.io.in, 0)
  poke(c.io.clock, 0)  // latch on this edge
  step(1)
  expect(c.io.out, 0)

  poke(c.io.in, 1)
  poke(c.io.clock, 1)  // should NOT latch on this edge
  step(1)
  expect(c.io.out, 0)
  poke(c.io.clock, 0)  // latch on this edge
  step(1)
  expect(c.io.out, 1)

  poke(c.io.enable, 0)  // check disable
  poke(c.io.in, 0)

  poke(c.io.clock, 1)
  step(1)
  expect(c.io.out, 1)
  poke(c.io.clock, 0)  // latch on this edge
  step(1)
  expect(c.io.out, 1)
}

class NegativeEdgeLatchUIntTest(val c: NegativeEdgeLatchTestModule[UInt]) extends PeekPokeTester(c) {
  poke(c.io.clock, 1)  // reset to high
  poke(c.io.enable, 1)

  poke(c.io.in, 0)
  poke(c.io.clock, 0)  // latch on this edge
  step(1)
  expect(c.io.out, 0)

  poke(c.io.in, 3)
  poke(c.io.clock, 1)  // should NOT latch on this edge
  step(1)
  expect(c.io.out, 0)
  poke(c.io.clock, 0)  // latch on this edge
  step(1)
  expect(c.io.out, 3)

  poke(c.io.in, 2)
  poke(c.io.clock, 1)  // should NOT latch on this edge
  step(1)
  expect(c.io.out, 3)
  poke(c.io.clock, 0)  // latch on this edge
  step(1)
  expect(c.io.out, 2)

  poke(c.io.enable, 0)  // check disable
  poke(c.io.in, 1)

  poke(c.io.clock, 1)
  step(1)
  expect(c.io.out, 2)
  poke(c.io.clock, 0)  // latch on this edge
  step(1)
  expect(c.io.out, 2)
}

class NegativeEdgeLatchTestModule[T <: Data](dataType: T) extends Module {
  class ModIO extends Bundle {
    val in = Input(dataType)
    val enable = Input(Bool())
    val out = Output(dataType)
    val clock = Input(Bool())
  }

  class Inner(mod_clock: Clock) extends Module(override_clock=Some(mod_clock)) {
    val io = IO(new ModIO)
    io.out := NegativeEdgeLatch(clock, io.in, io.enable)
  }

  val io = IO(new ModIO)
  val mod = Module(new Inner(io.clock.asClock))
  io <> mod.io
}

class NegativeEdgeLatchSpec extends ChiselFlatSpec {
  "NegativeEdgeLatch with a Bool" should "work" in {
    Driver(() => new NegativeEdgeLatchTestModule(Bool()), backendType="verilator") {
      c => new NegativeEdgeLatchBoolTest(c)
    } should be (true)
  }
  "NegativeEdgeLatch with a UInt" should "work" in {
    Driver(() => new NegativeEdgeLatchTestModule(UInt(2.W)), backendType="verilator") {
      c => new NegativeEdgeLatchUIntTest(c)
    } should be (true)
  }
}

class ClockedCounter4Test(val c: ClockedCounterTestModule) extends PeekPokeTester(c) {
  // Reset to known state
  poke(c.io.in, false)
  step(1)
  expect(c.io.out, 0)

  // Simple transition
  poke(c.io.in, true)
  step(1)
  expect(c.io.out, 1)

  // Main clock runs without counter transitioning
  step(1)
  expect(c.io.out, 1)
  step(1)
  expect(c.io.out, 1)

  // No counting on falling edge
  poke(c.io.in, false)
  step(1)
  expect(c.io.out, 1)

  // More transitions and overflow test
  poke(c.io.in, true)
  step(1)
  expect(c.io.out, 2)
  poke(c.io.in, false)
  step(1)
  expect(c.io.out, 2)

  poke(c.io.in, true)
  step(1)
  expect(c.io.out, 3)
  poke(c.io.in, false)
  step(1)
  expect(c.io.out, 3)

  poke(c.io.in, true)
  step(1)
  expect(c.io.out, 0)
  poke(c.io.in, false)
  step(1)
  expect(c.io.out, 0)
}

class ClockedCounter3Test(val c: ClockedCounterTestModule) extends PeekPokeTester(c) {
  // Reset to known state
  poke(c.io.in, false)
  step(1)
  expect(c.io.out, 0)

  // Simple transition
  poke(c.io.in, true)
  step(1)
  expect(c.io.out, 1)

  // Main clock runs without counter transitioning
  step(1)
  expect(c.io.out, 1)
  step(1)
  expect(c.io.out, 1)

  // No counting on falling edge
  poke(c.io.in, false)
  step(1)
  expect(c.io.out, 1)

  // More transitions and overflow test
  poke(c.io.in, true)
  step(1)
  expect(c.io.out, 2)
  poke(c.io.in, false)
  step(1)
  expect(c.io.out, 2)

  poke(c.io.in, true)
  step(1)
  expect(c.io.out, 0)
  poke(c.io.in, false)
  step(1)
  expect(c.io.out, 0)
}

class ClockedCounterTestModule(counts: Int) extends Module {
  class ModIO extends Bundle {
    val in = Input(Bool())
    val out = Output(UInt(log2Up(counts).W))
  }
  val io = IO(new ModIO)
  io.out := ClockedCounter(io.in, counts, 0)
}

class ClockedCounterSpec extends ChiselFlatSpec {
  "ClockedCounter with 4 counts" should "work" in {
    Driver(() => new ClockedCounterTestModule(4), backendType="verilator") {
      c => new ClockedCounter4Test(c)
    } should be (true)
  }
  "ClockedCounter with 3 counts" should "work" in {
    Driver(() => new ClockedCounterTestModule(3), backendType="verilator") {
      c => new ClockedCounter3Test(c)
    } should be (true)
  }
}
