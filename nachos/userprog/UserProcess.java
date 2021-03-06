package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
        createStdIO();
        UserKernel.registerProcess(this);
        // int numPhysPages = Machine.processor().getNumPhysPages();
        // pageTable = new TranslationEntry[numPhysPages];
        // for (int i = 0; i < numPhysPages; i++)
        //     pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
    }

    /**
     * Allocate and return a new process of the correct class. The class name is
     * specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    public void createStdIO() {
        FileDescriptor fd;

        fd = fds.alloc();
        fd.filename = "__STDIN__";
        fd.impl = UserKernel.console.openForReading();

        Lib.assertTrue(fd.id == FileDescriptorPool.STDIN);
        Lib.assertTrue(fd.impl != null);

        fd = fds.alloc();
        fd.filename = "__STDOUT__";
        fd.impl = UserKernel.console.openForWriting();

        Lib.assertTrue(fd.id == FileDescriptorPool.STDOUT);
        Lib.assertTrue(fd.impl != null);

        if (false) {
            fd = fds.alloc();
            fd.filename = "__STDERR__";
            fd.impl = UserKernel.console.openForWriting();

            Lib.assertTrue(fd.id == FileDescriptorPool.STDERR);
            Lib.assertTrue(fd.impl != null);
        }
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param name
     *            the name of the file containing the executable.
     * @param args
     *            the arguments to pass to the executable.
     * @return <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        if (!load(name, args))
            return false;

        this.ownerThread = new UThread(this);
        this.ownerThread.setName(name).fork();

        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read at
     * most <tt>maxLength + 1</tt> bytes from the specified address, search for
     * the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param vaddr
     *            the starting virtual address of the null-terminated string.
     * @param maxLength
     *            the maximum number of characters in the string, not including
     *            the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was
     *         found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr
     *            the first byte of virtual memory to read.
     * @param data
     *            the array where the data will be stored.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no data
     * could be copied).
     *
     * @param vaddr
     *            the first byte of virtual memory to read.
     * @param data
     *            the array where the data will be stored.
     * @param offset
     *            the first byte to write in the array.
     * @param length
     *            the number of bytes to transfer from virtual memory to the
     *            array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
        int vpn = Machine.processor().pageFromAddress(vaddr);
        int addrOffset = Machine.processor().offsetFromAddress(vaddr);

        // System.out.println("vpn" + vpn + " num" + numPages + " aaa" + vaddr);
        if (!(vpn >= 0 && vpn < numPages) || !(addrOffset >= 0 && addrOffset < pageSize)) {
            // System.out.println("vpn" + vpn + " num" + numPages);
            return 0;
        }

        // System.out.println("addr_offset: " + addrOffset + " length:" + length + " page size: " + pageSize);
        int amount = 0;
        while (addrOffset + length >= pageSize && vpn < numPages) {
            int num = pageSize - addrOffset;
            amount += readPhysicalMemory(pageTable[vpn].ppn * pageSize + addrOffset, data, offset, num);
            length -= num;
            offset += num;
            vpn += 1;
            addrOffset = 0;
        }
        amount += readPhysicalMemory(pageTable[vpn].ppn * pageSize + addrOffset, data, offset, length);
        return amount;
    }
    public int readPhysicalMemory(int paddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
        byte[] memory = Machine.processor().getMemory();

        if (paddr < 0 || paddr >= memory.length)
            return 0;

        int amount = Math.min(length, memory.length - paddr);
        System.arraycopy(memory, paddr, data, offset, amount);

        return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr
     *            the first byte of virtual memory to write.
     * @param data
     *            the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no data
     * could be copied).
     *
     * @param vaddr
     *            the first byte of virtual memory to write.
     * @param data
     *            the array containing the data to transfer.
     * @param offset
     *            the first byte to transfer from the array.
     * @param length
     *            the number of bytes to transfer from the array to virtual
     *            memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        int vpn = Machine.processor().pageFromAddress(vaddr);
        int addrOffset = Machine.processor().offsetFromAddress(vaddr);

        if (!(vpn >= 0 && vpn < numPages) || !(addrOffset >= 0 && addrOffset < pageSize)) {
            return 0;
        }

        int amount = 0;
        while (addrOffset + length >= pageSize && vpn < numPages){
            int num = pageSize - addrOffset;
            amount += writePhysicalMemory(pageTable[vpn].ppn * pageSize + addrOffset, data, offset, num);
            length -= num;
            offset += num;
            vpn += 1;
            addrOffset = 0;
        }
        amount += writePhysicalMemory(pageTable[vpn].ppn * pageSize + addrOffset, data, offset, length);
        return amount;
    }
    public int writePhysicalMemory(int paddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        if (paddr < 0 || paddr >= memory.length)
            return 0;

        int amount = Math.min(length, memory.length - paddr);
        System.arraycopy(data, offset, memory, paddr, amount);

        return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param name
     *            the name of the file containing the executable.
     * @param args
     *            the arguments to pass to the executable.
     * @return <tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }

        try {
            coff = new Coff(executable);
        } catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
            numPages += section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        numPages += stackPages;
        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        numPages++;

        pageTable = new TranslationEntry[numPages];

        if (!loadSections())
            return false;

        coff.close();

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
            stringOffset += 1;
        }

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be run
     * (this is the last step in process initialization that can fail).
     *
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        if (UserKernel.memoryAllocator.notEnoughPages(numPages)){
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory"
                    + numPages + " " + UserKernel.memoryAllocator.getRemainPages());
            return false;
        }

        // load sections
        int index = 0;
        int lastVPN = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess,
                    "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;
                int ppn = UserKernel.memoryAllocator.getAvailablePage();
                pageTable[index ++] = new TranslationEntry(vpn, ppn, true, false, false, false);
                lastVPN = vpn;

                section.loadPage(i, ppn);
            }
        }

        while (index < numPages){
            int ppn = UserKernel.memoryAllocator.getAvailablePage();
            pageTable[index ++] = new TranslationEntry(++ lastVPN, ppn, true, false, false, false);
        }

        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        for (int i = 0; i < numPages; ++ i)
            UserKernel.memoryAllocator.addAvailablePage(pageTable[i].ppn);
        coff.close();
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of the
     * stack, set the A0 and A1 registers to argc and argv, respectively, and
     * initialize all other registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();

        // by default, everything's 0
        for (int i = 0; i < processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {
        Machine.halt();

        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }

    private int handleCreate(int a0) {
        if (a0 <= 0) {
            return -1;
        }

        String filename = readVirtualMemoryString(a0, maxArgStringLen);
        if (filename.length() == 0) {
            return -1;
        }

        for (FileDescriptor fd : fds.getAll()) {
            if (fd.filename.equals(filename) && fd.needRemove) {
                return -1;
            }
        }

        Lib.debug(dbgProcess, "Syscall-create, filename=" + filename + ".");
        OpenFile impl = ThreadedKernel.fileSystem.open(filename, true);

        if (impl == null) {
            return -1;
        } else {
            FileDescriptor fd = fds.alloc();
            if (fd == null) {
                impl.close();
                return -1;
            } else {
                fd.filename = filename;
                fd.impl = impl;
                return fd.id;
            }
        }
    }

    private int handleOpen(int a0) {
        if (a0 <= 0) {
            return -1;
        }

        String filename = readVirtualMemoryString(a0, maxArgStringLen);
        if (filename.length() == 0) {
            return -1;
        }

        for (FileDescriptor fd : fds.getAll()) {
            if (fd.filename.equals(filename) && fd.needRemove) {
                return -1;
            }
        }

        Lib.debug(dbgProcess, "Syscall-open, filename=" + filename + ".");
        OpenFile impl = ThreadedKernel.fileSystem.open(filename, false);

        if (impl == null) {
            return -1;
        } else {
            FileDescriptor fd = fds.alloc();
            if (fd == null) {
                impl.close();
                return -1;
            } else {
                fd.filename = filename;
                fd.impl = impl;
                return fd.id;
            }
        }
    }

    private int handleRead(int a0, int vaddr, int bufSize) {
        Lib.debug(dbgProcess, "Syscall-read, fd=" + a0 + ".");

        FileDescriptor fd = fds.get(a0);

        if (fd == null || fd.isEmpty() || vaddr <= 0 || bufSize < 0) {
            return -1;
        }

        byte []buf = new byte[bufSize];
        // int length = fd.impl.read(fd.position, buf, 0, buf.length);
        int length = fd.impl.read(buf, 0, buf.length);
        if (length < 0) {
            return -1;
        }

        // fd.position += length;
        int actual_length = writeVirtualMemory(vaddr, buf, 0, length);
        Lib.assertTrue(length == actual_length);

        return length;
    }

    private int handleWrite(int a0, int vaddr, int bufSize) {
        Lib.debug(dbgProcess, "Syscall-write, fd=" + a0 + ".");

        FileDescriptor fd = fds.get(a0);

        if (fd == null || fd.isEmpty() || vaddr <= 0 || bufSize < 0) {
            return -1;
        }

        byte []buf = new byte[bufSize];
        int length = readVirtualMemory(vaddr, buf);
        if (length < 0) {
            return -1;
        }

        // System.out.println("id " + a0 + " buflen " + length);
        // for (int i = 0; i < length; ++i) System.out.println("char " + i + " = " + buf[i]);
        // int actual_length = UserKernel.console.openForWriting().write(0, buf, 0, length);
        // int actual_length = fd.impl.write(fd.position, buf, 0, length);
        int actual_length = fd.impl.write(buf, 0, length);
        if (actual_length < 0) {
            return -1;
        }

        // fd.position += actual_length;

        return actual_length;
    }

    private int handleClose(int a0) {
        FileDescriptor fd = fds.get(a0);

        if (fd == null || fd.isEmpty()) {
            return -1;
        }

        fd.impl.close();
        int rc = 0;
        if (fd.needRemove) {
            boolean foundOpened = false;
            for (FileDescriptor fd2 : fds.getAll()) {
                if (fd2.filename.equals(fd.filename)) {
                    fd2.needRemove = true;
                    foundOpened = true;
                }
            }
            if (!foundOpened) {
                rc = ThreadedKernel.fileSystem.remove(fd.filename) ? 0 : -1;
            }
        }

        fds.free(a0);

        return rc;
    }

    private int handleUnlink(int a0) {
        if (a0 <= 0) {
            return -1;
        }

        String filename = readVirtualMemoryString(a0, maxArgStringLen);
        if (filename.length() == 0) {
            return -1;
        }

        for (FileDescriptor fd : fds.getAll()) {
            if (fd.filename.equals(filename) && fd.needRemove) {
                return -1;
            }
        }

        FileDescriptor fd;
        int rc = 0;

        fd = fds.get(filename);
        if (fd == null) {
            rc = ThreadedKernel.fileSystem.remove(filename) ? 0 : -1;
        } else {
            fd.needRemove = true;
        }

        return rc;
    }

    private int handleExec(int a0, int argc, int argv) {
        if (a0 <= 0) {
            return -1;
        }

        String filename = readVirtualMemoryString(a0, maxArgStringLen);
        if (filename == null) {
            Lib.debug(dbgProcess, "Exec: invalid filename.");
            return -1;
        }

        if (!filename.endsWith(".coff")) {
            Lib.debug(dbgProcess, "Exec: invalid filename, should ends with `.coff`.");
            return -1;
        }

        if (argc < 0 || argv <= 0) {
            Lib.debug(dbgProcess, "Exec: invalid argc < 0.");
            return -1;
        }

        String strArgv[] = new String[argc];
        byte byteAddr[] = new byte[4];
        for (int i = 0; i < argc; ++i) {
            int addrBytes = readVirtualMemory(argv + i * 4, byteAddr);
            if (addrBytes != 4) {
                Lib.debug(dbgProcess, "Exec: invalid argv, addr bytes != 4.");
                return -1;
            }

            int addr = Lib.bytesToInt(byteAddr, 0);
            if (addr <= 0) {
                Lib.debug(dbgProcess, "Exec: invalid argv, addr < 0.");
                return -1;
            }
            strArgv[i] = readVirtualMemoryString(addr, maxArgStringLen);
        }

        UserProcess p = UserProcess.newUserProcess();
        p.ppid = pid;
        children.add(p.pid);

        boolean rc = p.execute(filename, strArgv);
        return rc ? p.pid : -1;
    }

    private int handleJoin(int childPid, int statusBuf) {
        boolean isChild = false;
        for (int pid : children) {
            if (pid == childPid) {
                isChild = true;
                break;
            }
        }

        if (!isChild || statusBuf <= 0) {
            Lib.debug(dbgProcess, "Join: invalid child pid.");
            return -1;
        }

        children.remove(new Integer(childPid));
        UserProcess p = UserKernel.getProcess(childPid);
        if (p == null){
            return -1;
        }
        p.ownerThread.join();
        UserKernel.unregisterProcess(p);

        byte byteStatus[] = Lib.bytesFromInt(p.exitStatus);
        int statusBytes = writeVirtualMemory(statusBuf, byteStatus);
        if (statusBytes != 4) {
            return -1;
        }
        return 0;
    }

    private int handleExit(int status) {
        // Close all file descriptors
        for (FileDescriptor fd : fds.getAll()) {
            if (!fd.isEmpty()) {
                handleClose(fd.id);
            }
        }

        // All children
        while (!children.isEmpty()) {
            int childPid = children.removeFirst();
            UserProcess p = UserKernel.getProcess(childPid);
            p.ppid = UserKernel.getRootProcess().pid;
        }

        // Unload sections
        this.unloadSections();

        this.exitStatus = status;

        if (this.pid == UserKernel.getRootProcess().pid) {
            Kernel.kernel.terminate();
        } else {
            KThread.currentThread().finish();
        }

        Lib.assertNotReached();
        return 0;
    }

    private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2, syscallJoin = 3, syscallCreate = 4,
            syscallOpen = 5, syscallRead = 6, syscallWrite = 7, syscallClose = 8, syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr>
     * <td>syscall#</td>
     * <td>syscall prototype</td>
     * </tr>
     * <tr>
     * <td>0</td>
     * <td><tt>void halt();</tt></td>
     * </tr>
     * <tr>
     * <td>1</td>
     * <td><tt>void exit(int status);</tt></td>
     * </tr>
     * <tr>
     * <td>2</td>
     * <td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td>
     * </tr>
     * <tr>
     * <td>3</td>
     * <td><tt>int  join(int pid, int *status);</tt></td>
     * </tr>
     * <tr>
     * <td>4</td>
     * <td><tt>int  creat(char *name);</tt></td>
     * </tr>
     * <tr>
     * <td>5</td>
     * <td><tt>int  open(char *name);</tt></td>
     * </tr>
     * <tr>
     * <td>6</td>
     * <td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td>
     * </tr>
     * <tr>
     * <td>7</td>
     * <td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td>
     * </tr>
     * <tr>
     * <td>8</td>
     * <td><tt>int  close(int fd);</tt></td>
     * </tr>
     * <tr>
     * <td>9</td>
     * <td><tt>int  unlink(char *name);</tt></td>
     * </tr>
     * </table>
     *
     * @param syscall
     *            the syscall number.
     * @param a0
     *            the first syscall argument.
     * @param a1
     *            the second syscall argument.
     * @param a2
     *            the third syscall argument.
     * @param a3
     *            the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        Lib.debug(dbgProcess, "Doing syscall " + syscall);
        switch (syscall) {
        case syscallHalt:
            return handleHalt();
        case syscallCreate:
            return handleCreate(a0);
        case syscallOpen:
            return handleOpen(a0);
        case syscallRead:
            return handleRead(a0, a1, a2);
        case syscallWrite:
            return handleWrite(a0, a1, a2);
        case syscallClose:
            return handleClose(a0);
        case syscallUnlink:
            return handleUnlink(a0);
        case syscallExec:
            return handleExec(a0, a1, a2);
        case syscallJoin:
            return handleJoin(a0, a1);
        case syscallExit:
            return handleExit(a0);

        default:
            Lib.debug(dbgProcess, "Unknown syscall " + syscall);
            Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }

    public class FileDescriptor {
        public FileDescriptor(int id) {
            this.id = id;
        }

        public void reset() {
            this.filename = "";
            this.impl = null;
            // this.position = 0;
            this.needRemove = false;
        }

        public boolean isEmpty() {
            return this.impl == null;
        }

        public int id;
        public String filename = "";
        public OpenFile impl = null;
        // public int position = 0;
        public boolean needRemove = false;
    }

    public class FileDescriptorPool {
        static final int maxFds = 16;
        static final int STDIN = 0;
        static final int STDOUT = 1;
        static final int STDERR = 2;

        private FileDescriptor []pool = new FileDescriptor[maxFds];

        public FileDescriptorPool() {
            for (int i = 0; i < maxFds; ++i) {
                pool[i] = new FileDescriptor(i);
            }
        }

        public FileDescriptor alloc() {
            for (int i = 0; i < maxFds; ++i) {
                if (pool[i].isEmpty()) {
                    return pool[i];
                }
            }
            return null;
        }

        public int free(int fd) {
            Lib.assertTrue(!pool[fd].isEmpty());
            pool[fd].reset();
            return 0;
        }

        public FileDescriptor get(int id) {
            if (id < 0 || id >= maxFds) {
                return null;
            }
            return pool[id];
        }

        public FileDescriptor []getAll() {
            return pool;
        }

        public FileDescriptor get(String filename) {
            if (filename == "") {
                return null;
            }

            for (int i = 0; i < maxFds; ++i) {
                if (pool[i].filename.equals(filename)) {
                    Lib.assertTrue(!pool[i].isEmpty());
                    return pool[i];
                }
            }
            return null;
        }
    };

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The <i>cause</i> argument
     * identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param cause
     *            the user exception that occurred.
     */
    public void handleException(int cause) {
        Processor processor = Machine.processor();

        switch (cause) {
        case Processor.exceptionSyscall:
            int result = handleSyscall(processor.readRegister(Processor.regV0), processor.readRegister(Processor.regA0),
                    processor.readRegister(Processor.regA1), processor.readRegister(Processor.regA2),
                    processor.readRegister(Processor.regA3));
            processor.writeRegister(Processor.regV0, result);
            processor.advancePC();
            break;

        default:
            Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
            handleExit(-1);
            Lib.assertNotReached("Unexpected exception");
        }
    }

    protected FileDescriptorPool fds = new FileDescriptorPool();

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;

    private int initialPC, initialSP;
    private int argc, argv;

    public int pid = 0;
    public int ppid = 0;
    public int exitStatus;
    private UThread ownerThread;
    public LinkedList<Integer> children = new LinkedList<Integer>();

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    private static final int maxArgStringLen = 256;
}
