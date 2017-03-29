// See LICENSE for license details.

package jtag.test

import org.scalatest._

import chisel3.iotesters.experimental.{ImplicitPokeTester, VerilatorBackend}

import chisel3._
import chisel3.util._
import jtag._

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
  
  override def desiredName = "NegativeEdgeLatchTestModule" + dataType.getClass().getSimpleName()  // TODO needed to not break verilator 
}

class NegativeEdgeLatchSpec extends FlatSpec with ImplicitPokeTester {
  "NegativeEdgeLatch with a Bool" should "work" in {
    test(new NegativeEdgeLatchTestModule(Bool()), testerBackend=VerilatorBackend) { implicit t => c =>
      poke(c.io.clock, 1)  // reset to high
      poke(c.io.enable, 1)
    
      poke(c.io.in, 0)
      poke(c.io.clock, 0)  // latch on this edge
      step(1)
      check(c.io.out, 0)
    
      poke(c.io.in, 1)
      poke(c.io.clock, 1)  // should NOT latch on this edge
      step(1)
      check(c.io.out, 0)
      poke(c.io.clock, 0)  // latch on this edge
      step(1)
      check(c.io.out, 1)
    
      poke(c.io.enable, 0)  // check disable
      poke(c.io.in, 0)
    
      poke(c.io.clock, 1)
      step(1)
      check(c.io.out, 1)
      poke(c.io.clock, 0)  // latch on this edge
      step(1)
      check(c.io.out, 1)
    }
  }
  "NegativeEdgeLatch with a UInt" should "work" in {
    test(new NegativeEdgeLatchTestModule(UInt(2.W)), testerBackend=VerilatorBackend) { implicit t => c =>
      poke(c.io.clock, 1)  // reset to high
      poke(c.io.enable, 1)
    
      poke(c.io.in, 0)
      poke(c.io.clock, 0)  // latch on this edge
      step(1)
      check(c.io.out, 0)
    
      poke(c.io.in, 3)
      poke(c.io.clock, 1)  // should NOT latch on this edge
      step(1)
      check(c.io.out, 0)
      poke(c.io.clock, 0)  // latch on this edge
      step(1)
      check(c.io.out, 3)
    
      poke(c.io.in, 2)
      poke(c.io.clock, 1)  // should NOT latch on this edge
      step(1)
      check(c.io.out, 3)
      poke(c.io.clock, 0)  // latch on this edge
      step(1)
      check(c.io.out, 2)
    
      poke(c.io.enable, 0)  // check disable
      poke(c.io.in, 1)
    
      poke(c.io.clock, 1)
      step(1)
      check(c.io.out, 2)
      poke(c.io.clock, 0)  // latch on this edge
      step(1)
      check(c.io.out, 2)
    }
  }
}

class ClockedCounterTestModule(counts: Int) extends Module {
  class ModIO extends Bundle {
    val in = Input(Bool())
    val out = Output(UInt(log2Up(counts).W))
  }
  val io = IO(new ModIO)
  io.out := ClockedCounter(io.in, counts, 0)
  
  override def desiredName = "ClockedCounterTestModule" + counts  // TODO needed to not break verilator 
}

class ClockedCounterSpec extends FlatSpec with ImplicitPokeTester {
  "ClockedCounter with 4 counts" should "work" in {
    test(new ClockedCounterTestModule(4), testerBackend=VerilatorBackend) { implicit t => c =>
      // Reset to known state
      poke(c.io.in, false)
      step(1)
      check(c.io.out, 0)
    
      // Simple transition
      poke(c.io.in, true)
      step(1)
      check(c.io.out, 1)
    
      // Main clock runs without counter transitioning
      step(1)
      check(c.io.out, 1)
      step(1)
      check(c.io.out, 1)
    
      // No counting on falling edge
      poke(c.io.in, false)
      step(1)
      check(c.io.out, 1)
    
      // More transitions and overflow test
      poke(c.io.in, true)
      step(1)
      check(c.io.out, 2)
      poke(c.io.in, false)
      step(1)
      check(c.io.out, 2)
    
      poke(c.io.in, true)
      step(1)
      check(c.io.out, 3)
      poke(c.io.in, false)
      step(1)
      check(c.io.out, 3)
    
      poke(c.io.in, true)
      step(1)
      check(c.io.out, 0)
      poke(c.io.in, false)
      step(1)
      check(c.io.out, 0)  
    }
  }
  "ClockedCounter with 3 counts" should "work" in {
    test(new ClockedCounterTestModule(3), testerBackend=VerilatorBackend) { implicit t => c =>
      // Reset to known state
      poke(c.io.in, false)
      step(1)
      check(c.io.out, 0)
    
      // Simple transition
      poke(c.io.in, true)
      step(1)
      check(c.io.out, 1)
    
      // Main clock runs without counter transitioning
      step(1)
      check(c.io.out, 1)
      step(1)
      check(c.io.out, 1)
    
      // No counting on falling edge
      poke(c.io.in, false)
      step(1)
      check(c.io.out, 1)
    
      // More transitions and overflow test
      poke(c.io.in, true)
      step(1)
      check(c.io.out, 2)
      poke(c.io.in, false)
      step(1)
      check(c.io.out, 2)
    
      poke(c.io.in, true)
      step(1)
      check(c.io.out, 0)
      poke(c.io.in, false)
      step(1)
      check(c.io.out, 0)
    }
  }
}
