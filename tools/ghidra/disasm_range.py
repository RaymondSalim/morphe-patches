# Ghidra headless script (Jython).
# Usage: analyzeHeadless ... -postScript disasm_range.py <startAddrHex> <lengthHex>
# Disassembles [start, start+length) and prints one instruction per line.
from ghidra.program.model.address import AddressSet
from ghidra.app.cmd.disassemble import DisassembleCommand

args = getScriptArgs()
start = currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(int(args[0], 16))
length = int(args[1], 16)
end = start.add(length)
DisassembleCommand(start, AddressSet(start, end), True).applyTo(currentProgram, monitor)

for ins in currentProgram.getListing().getInstructions(start, True):
    print("%s  %s" % (ins.getAddress(), ins))
    if ins.getAddress() >= end:
        break
