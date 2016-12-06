# chisel-jtag
JTAG TAP generator for (and written in) Chisel

## Introduction
JTAG is a communications / transport protocol used for debugging chips by providing a scan chain of connected devices and an instruction register and data register(s) per device. A common use of JTAG is for boundary scan (to verify component connectivity on a circuit board), but JTAG is also used as a communication protocol for debugging (i.e. flashing and stepping processors).

### This Design
This design implements the JTAG TAP (Test Access Port) controller, including the JTAG state machine, instruction registers, and ports to connect user data registers. A TAP generator is also included, which provides instruction register decode and maps user scan chains to instructions.

The implementation is clocked from the JTAG clock, allowing the JTAG block to function independently of whether the system clock is running (or even works). This is NOT an optimized design, focusing instead on readability and an easy-to-use API. In particular, timing hasn't been optimized at a fine level and there's little attention to preventing intra-cycle (combinational) glitches. Users will need to register outputs in cases where glitches are not acceptable and robust clock crossing constructs (like async FIFOs) should be used to interface with the main system.

### Experimental!
This is still an experimental design and the API is still subject to change.

## Using
### The Basics
Import the necessary packages:

```
import jtag._
```

Define your test block module, which encapsulates the JTAG TAP controller, user data scan chains, and any additional logic needed. This block must be clocked from JTAG TCK and should expose the JTAG IO pins (TCK, TMS, TDI, TDO).

```
class JtagBlock (modClock: Clock) extends Module(override_clock=Some(modClock)) {
  val io = IO(new JtagIO)
  // JtagIO is a Bundle of {TCK, TMS, TDI, and TDO},
  // where TDO is a Tristate Bundle of {data and driven}
}
```

Invoke the TAP generator:

```
// Generate a JTAG TAP with a 2-bit IR and IDCODE instruction code of b00.
// BYPASS chain is selected for all other instruction codes.
val tap = JtagTapGenerator(2, Map(), idcode=Some((0, JtagIdcode(0xA, 0x123, 0x42))))
io.jtag <> tap.io.jtag
io.output <> tap.io.output
io.status <> tap.io.status
```

JtagTapGenerator instantiates a TAP controller block (which is its return value) and some predefined data scan chain blocks (like the required BYPASS and optional IDCODE registers). It also generates the necessary muxes between the controller and the connected data chains (selected by currently active instruction). On a Module hierarchy level, this means that the generated TAP controller, generated data scan chains (BYPASS and possibly IDCODE), and any user-defined data chains are directly inside the same Module.

The arguments are:
- `irLength`: Int - length, in bits, of the instruction register.
- `instructions`: Map[Chain, BigInt] - a Map of data registers (Chains) to the instruction code that selects them.
- `idcode`: Option[(BigInt, BigInt)] - optional IDCODE generator. A value of None means to not generate a IDCODE register (and BYPASS, with an instruction code of all ones, will be selected as the initial instruction), while a value of (instruction code, idcode) generates the 32-bit IDCODE register and selects its corresponding instruction code as the initial instruction.

All unused instruction codes will select the BYPASS register.

### User Data Chains
`Chain` is the base trait for user data shift registers / scan chains. `Chain`s provide a `chainIn` and `chainOut` `ShifterIO`, consisting of the data bit into the chain and control signals (`capture` and `update`). The JTAG TAP controller exposes a chain output and input `ShifterIO` to be connected to the currently active data register `Chain`.
- `capture` and `update` are high while in their corresponding JTAG states (one full TCK cycle). These status signals are always present in `ShifterIO` regardless of whether a chain supports capture and/or update.
- The API design is to allow configurable concatenation of multiple chains, the details of this are still TBD.

The current implemented `Chain`s are:
- `JtagBypassChain()`: single stage bypass register with hard-coded zero on capture and no update. Users should not need to instantiate this, one is automatically provided by the generator.
- `CaptureUpdateChain(n)`: a scan chain with parallel capture (load into shifter) and update (shifter valid).
  - `n`: number of bits (or stages in the shift register)
  - `CaptureUpdateChain` provides these IOs:
    - `capture`: a `CaptureIO` bundle, consisting of the parallel input `bits` and a `capture` Bool signal (which is a status indicator only). The parallel input should always be valid since `capture` may be asserted at any time.
    - `update`: a `ValidIO` bundle, consisting of the parallel output `bits` and a `valid` Bool signal (which is high for only one TCK cycle, in the JTAG Update-* state). Note that `bits` output is _only+ guaranteed valid when `valid` is high. This may be fed (for example) into a (clock-crossing) FIFO or a register which updates on `valid`.

Example usage with TAP generator invocation:

```
val myFunRegister = Reg(UInt(8.W))  // 8-bit example system register

// Generate a 8-bit data scan chain with parallel capture and update
val myDataChain = Module(new CaptureUpdateChain(8))

myDataChain.io.capture.bits := 0x42.U  // always capture 0x42
when (myDataChain.io.update.valid) {
  myFunRegister := myDataChain.io.update.bits  // latch system register with scanned-in data
}

// Generate a JTAG TAP with a 2-bit IR and select myDataChain for scan when instruction code b01 is
// active. Don't generate an IDCODE chain. BYPASS chain is selected for all other instruction codes.
val tap = JtagTapGenerator(2, Map(myDataChain -> 1))
io.jtag <> tap.io.jtag
io.output <> tap.io.output
io.status <> tap.io.status
```

### Misc Notes

- There's a lot of elaboration-time error checking to prevent parameters that are contrary to the spec. For example, the generator will `require` out if attempting to set an IDCODE that conflicts with the JTAG spec's reserved dummy code.
  - It is a bug if the generator can generate non-spec-compliant designs - if this happens, please file an issue!
  - Exception: boundary-scan is currently not implemented. Please don't file a bug for that.

## Hardware Verification
This generator has been used in these designs:
- None yet.

Planned:
- ICE40HX8K-B-EVN (Lattice iCE40 FPGA)

## TODOs
Some features are yet to be implemented:
- Ability to annotate arbitrary registers in arbitrary modules to be written to / read from using JTAG. This will probably be implemented as a FIRRTL transform.
- Capture-only and update-only scan chains. This is mainly an optimization.
- Boundary-scan, technically needed to chain JTAG compliance. This may be difficult as it requires clock crossing domains and messing up can result in logic glitches that fry a chip.
- Other JTAG optional instructions, like TMP.
- Asynchronous reset through TRST. This is an optional part of the spec and is of low importance.

Some features need a bit more thought:
- Arbiters / arbiter generators so a data chain can act as a low priority (relative to the system) bus master. Possibly also a FIRRTL transform to hook up such an arbiter to an existing system bus.
- Data chains with always-valid data output and glitchless updates, allowing the output to be used as a register. Possibly also a FIRRTL transform to replace a system register with such a register.

Some cleaning up is also to be done:
- Multiclock using chisel3's proposed `withClock` API, when it's done.
