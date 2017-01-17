# chisel-jtag
JTAG TAP generator for (and written in) Chisel

## Introduction
JTAG is a communications / transport protocol used for debugging chips by providing a scan chain of connected devices and an instruction register and data register(s) per device. A common use of JTAG is for boundary scan (to verify component connectivity on a circuit board), but JTAG is also used as a communication protocol for debugging (i.e. flashing and stepping processors).

### This Design
This design implements the JTAG TAP (Test Access Port) controller, including the JTAG state machine, instruction registers, and ports to connect user data registers. A TAP generator is also included, which provides instruction register decode and maps user scan chains to instructions.

The implementation is clocked from the JTAG clock, allowing the JTAG block to function independently of whether the system clock is running (or even works). This is NOT an optimized design, focusing instead on readability and an easy-to-use API. In particular, timing hasn't been optimized at a fine level and there's little attention to preventing intra-cycle (combinational) glitches. Users will need to register outputs in cases where glitches are not acceptable and robust clock crossing constructs (like async FIFOs) should be used to interface with the main system.

### Experimental!
This is still an experimental design and the API is still subject to change.

### *Some documentation may be out of date.*

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
val tapIo = JtagTapGenerator(2, Map(), idcode=Some((0, JtagIdcode(0xA, 0x123, 0x42))))
io.jtag <> tap.io.jtag
// tapIo.output.instruction provides the current active instruction, negative-edge latched per the spec.
```

JtagTapGenerator instantiates a TAP controller block (returning its IO) and some predefined data scan chain blocks (like the required BYPASS and optional IDCODE registers). It also generates the necessary muxes between the controller and the connected data chains (selected by currently active instruction). On a Module hierarchy level, this means that the generated TAP controller, generated data scan chains (BYPASS and possibly IDCODE), and any user-defined data chains are directly inside the same Module.

The arguments are:
- `irLength`: Int - length, in bits, of the instruction register.
- `instructions`: Map[Chain, BigInt] - a Map of data registers (Chains) to the instruction code that selects them. Multiple instructions may select the same chains for scan, for example if the instructions have different state actions.
- `idcode`: Option[(BigInt, BigInt)] - optional IDCODE generator. A value of None means to not generate a IDCODE register (and BYPASS, with an instruction code of all ones, will be selected as the initial instruction), while a value of (instruction code, idcode) generates the 32-bit IDCODE register and selects its corresponding instruction code as the initial instruction.

All unused instruction codes will select the BYPASS register.

### User Data Chains
`Chain` is the base trait for user data shift registers / scan chains. `Chain`s provide a `chainIn` and `chainOut` `ShifterIO`, consisting of the data bit into the chain and control signals (`capture` and `update`). The JTAG TAP controller exposes a chain output and input `ShifterIO` to be connected to the currently active data register `Chain`.
- `capture` and `update` are high while in their corresponding JTAG states (one full TCK cycle). These status signals are always present in `ShifterIO` regardless of whether a chain supports capture and/or update.
- The API design is to allow configurable concatenation of multiple chains, the details of this are still TBD.

The current implemented `Chain`s are:
- `JtagBypassChain()`: single stage bypass register with hard-coded zero on capture and no update. Users should not need to instantiate this, one is automatically provided by the generator.
- `CaptureUpdateChain(captureType, updateType)`, `CaptureUpdateChain(type)`: a scan chain with parallel capture (load into shifter) and update (shifter valid).
  - `captureType`, `updateType`, `type`: data model used for the capture and update types (or if only one is specified, it is used for both capture and update). The scan chain is the longer of the two input types and uses `fromBits` / `asUInt` ordering in ordering bits in `Aggregate` types. Capture is zero-padded at the most significant bits (as necessary), and update takes the least significant bits.
  - `CaptureUpdateChain` provides these IOs:
    - `capture`: a `CaptureIO` bundle, consisting of the parallel input `bits` and a `capture` Bool signal (which is a status indicator only). The parallel input should always be valid since `capture` may be asserted at any time.
    - `update`: a `ValidIO` bundle, consisting of the parallel output `bits` and a `valid` Bool signal (which is high for only one TCK cycle, in the JTAG Update-* state). Note that `bits` output is _only_ guaranteed valid when `valid` is high. This may be fed (for example) into a (clock-crossing) FIFO or a register which updates on `valid`.
- `CaptureChain(type)`: a scan chain with parallel capture (load into shifter) only. Arguments and interface are similar to `CaptureUpdateChain` with the capture portion only.

Example usage with TAP generator invocation:

```
// Generate a 8-bit data scan chain with parallel capture and update
val myDataChain = Module(new CaptureUpdateChain(UInt(8.W)))

val myFunRegister = Reg(UInt(8.W))  // 8-bit example system register
myDataChain.io.capture.bits := myFunReg  // capture contents of register
when (myDataChain.io.update.valid) {
  myFunRegister := myDataChain.io.update.bits  // update system register with scanned-in data
}

// Generate a JTAG TAP with a 2-bit IR and select myDataChain for scan when instruction code b01 is
// active. Don't generate an IDCODE chain. BYPASS chain is selected for all other instruction codes.
val tap = JtagTapGenerator(2, Map(myDataChain -> 1))
```

### Status
The Bundle returned by the TAP generator provides an `output` field providing (possibly useful) TAP status. `output` contains these fields:
- `state: JtagState`: current JTAG state, updated on the TCK rising edge.
  - *Note: this uses nonstandard Enum-like infrastructure and will be rewritten once Chisel improves base Enum support. Currently, values are specified like `JtagState.TestLogicReset.U` or `JtagSTate.RunTestIdle.U`. See [src/main/scala/JtagStateMachine.scala](src/main/scala/JtagStateMachine.scala) for all the values.*
  - Do NOT depend on any particular numeric encoding of states (use the enum abstraction) as this may be subject to change or optimization.
- `instruction: UInt`: current active instruction, updated on the TCK falling edge (as per the spec). This may be useful for logic that depends on the current instruction, like boundary-scan's EXTEST instruction.
- `reset: Bool`: high if the TAP is in the Test-Logic-Reset state. This should be used as the reset signal for any JTAG block logic, like captured registers.
  - *Note: until better clock-crossing support is implemented in Chisel, this must be done at a Module boundary*

*As the structure of these signals are not completely defined by the spec, this API is subject to change.*

### Reset
*This API has not been finalized and is subject to change pending Chisel asynchronous reset implementation and clock-crossing API improvements.*

`JtagTapGenerator` takes its (synchronous) Module reset signal from its containing Module. This works as expected, except that FSM is not affected by this signal. This is a ugly hack to allow Test-Logic-Reset to reset user logic without holding the TAP perpetually in reset, and will be removed when TRST is implemented. The TAP may start in an unknown state, and should be set to a known state externally with 5 TMS=1 transitions.

### Spec Compliance
Some requirements of the JTAG Spec [IEEE Std 1149.1-2013 (paywall / subscription)](https://standards.ieee.org/findstds/standard/1149.1-2013.html) are outside the abstraction boundary provided by this generator and must be handled at a higher level. Detailed notes for spec compliance are included in the `JtagTapGenerator` ScalaDoc. Main points are:
- TMS and TDI (inputs) must appear high when undriven.
- TDO must be tri-state and undriven except when shifting data.
- TAP controller must not be reset by system reset (but may share a power-on reset) .
- Boundary scan must be implemented for formal spec compliance. This isn't implemented yet.

Caveat emptor: no guarantees are made for formal spec compliance - these notes are provided on a best-effort basis. If you see anything wrong, please submit a pull request!

Many optional sections of the spec (like test mode presistence or reset instructions) have not been implemented. They may be developed if there is a good case supporting one of the Berkeley chip projects, but feel free to implement it and submit a pull request. I'd be happy to discuss high level implementation strategy beforehand to make it more likely that a pull request will be accepted and merged.

### Misc Notes

- There's a lot of elaboration-time error checking to prevent parameters that are contrary to the spec. For example, the generator will `require` out if attempting to set an IDCODE that conflicts with the JTAG spec's reserved dummy code.
  - It is a bug if the generator can generate non-spec-compliant designs - if this happens, please file an issue!
  - Exception: boundary-scan is currently not implemented. Please don't file a bug for that, it will be implemented eventually.

### Package Structure
No guarantees are made about the structure and contents of the `examples` folder. In particular, do NOT depend on its contents(such as the async tools) in your designs.

## Hardware Verification
This generator has been used in these designs:
- Example design on [ICE40HX8K-B-EVN (Lattice iCE40 FPGA) through Yosys](examples/ice40hx8k-yosys)

Planned:
- None currently.

## More Debugging Modules
Check out the [builtin-debuggers](https://github.com/ucb-art/builtin-debugger) repository, which contains generators for debugging blocks like a logic analyzer and pattern generator that you can instantiate on your chip or FPGA and connect it through JTAG.

## TODOs
Some features are yet to be implemented:
- Asynchronous reset through TRST, for ASICs without POR capability. This depends on cleaner asynchronous-reset register support in Chisel.
- Boundary-scan, technically needed to chain JTAG compliance. This may be difficult as it requires clock crossing domains and messing up can result in logic glitches that fry a chip.
- Ability to annotate arbitrary registers in arbitrary modules to be written to / read from using JTAG. This will probably be implemented as a FIRRTL transform.
- Update-only scan chains - this is mainly a small optimization.
- JTAG Route Controllers, to disable / enable specific TAPs.
- Other JTAG optional instructions, like TMP.
- Optionally positive edge clocked outputs, for internal TAP chains.

Some features need a bit more thought:
- Arbiters / arbiter generators so a data chain can act as a low priority (relative to the system) bus master. Possibly also a FIRRTL transform to hook up such an arbiter to an existing system bus.
- Data chains with always-valid data output and glitchless updates, allowing the output to be used as a register. Possibly also a FIRRTL transform to replace a system register with such a register.
- BSDL generation, and how it might integrate with tools like OpenOCD.

Some cleaning up is also to be done:
- Multiclock using chisel3's proposed `withClock` API, when it's done.
