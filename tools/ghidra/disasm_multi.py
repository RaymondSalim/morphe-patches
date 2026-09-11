# Ghidra headless script. Disassembles one or more [start, start+len) ranges
# and prints one instruction per line. Args: repeated <startAddrHex> <lenHex>.
# Addresses are Ghidra addresses (Castle Clashers project: dump.cs VA + 0x100000).
from ghidra.program.model.address import AddressSet
from ghidra.app.cmd.disassemble import DisassembleCommand

args = getScriptArgs()
af = currentProgram.getAddressFactory().getDefaultAddressSpace()
for i in range(0, len(args), 2):
    start = af.getAddress(int(args[i], 16))
    length = int(args[i + 1], 16)
    end = start.add(length)
    DisassembleCommand(start, AddressSet(start, end), True).applyTo(currentProgram, monitor)
    print('=== range %s len %#x ===' % (args[i], length))
    for ins in currentProgram.getListing().getInstructions(start, True):
        print('%s  %s' % (ins.getAddress(), ins))
        if ins.getAddress() >= end:
            break
