/**
 * MIT License
 *
 * Copyright (c) 2010 - 2020 The OSHI Project Contributors: https://github.com/oshi/oshi/graphs/contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package oshi.hardware.platform.windows;

import static oshi.util.Memoizer.defaultExpiration;
import static oshi.util.Memoizer.memoize;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.platform.win32.Kernel32; // NOSONAR squid:S1191
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.Psapi.PERFORMANCE_INFORMATION;
import com.sun.jna.platform.win32.VersionHelpers;
import com.sun.jna.platform.win32.COM.WbemcliUtil.WmiQuery;
import com.sun.jna.platform.win32.COM.WbemcliUtil.WmiResult;

import oshi.hardware.PhysicalMemory;
import oshi.hardware.VirtualMemory;
import oshi.hardware.common.AbstractGlobalMemory;
import oshi.util.platform.windows.WmiQueryHandler;
import oshi.util.platform.windows.WmiUtil;

/**
 * Memory obtained by Performance Info.
 */
public class WindowsGlobalMemory extends AbstractGlobalMemory {

    private static final Logger LOG = LoggerFactory.getLogger(WindowsGlobalMemory.class);

    private static final boolean IS_WINDOWS10_OR_GREATER = VersionHelpers.IsWindows10OrGreater();

    private final Supplier<PerfInfo> perfInfo = memoize(this::readPerfInfo, defaultExpiration());

    private final Supplier<VirtualMemory> vm = memoize(this::createVirtualMemory);

    enum PhysicalMemoryProperty {
        BANKLABEL, CAPACITY, SPEED, MANUFACTURER, SMBIOSMEMORYTYPE
    }

    // Pre-Win10
    enum PhysicalMemoryPropertyWin8 {
        BANKLABEL, CAPACITY, SPEED, MANUFACTURER, MEMORYTYPE
    }

    @Override
    public long getAvailable() {
        return perfInfo.get().available;
    }

    @Override
    public long getTotal() {
        return perfInfo.get().total;
    }

    @Override
    public long getPageSize() {
        return perfInfo.get().pageSize;
    }

    @Override
    public VirtualMemory getVirtualMemory() {
        return vm.get();
    }

    private VirtualMemory createVirtualMemory() {
        return new WindowsVirtualMemory(getPageSize());
    }

    @Override
    public PhysicalMemory[] getPhysicalMemory() {
        PhysicalMemory[] physicalMemoryArray;
        WmiQueryHandler wmiQueryHandler = WmiQueryHandler.createInstance();
        if (IS_WINDOWS10_OR_GREATER) {
            WmiQuery<PhysicalMemoryProperty> physicalMemoryQuery = new WmiQuery<>("Win32_PhysicalMemory",
                    PhysicalMemoryProperty.class);
            WmiResult<PhysicalMemoryProperty> bankMap = wmiQueryHandler.queryWMI(physicalMemoryQuery);

            physicalMemoryArray = new PhysicalMemory[bankMap.getResultCount()];
            for (int index = 0; index < bankMap.getResultCount(); index++) {
                String bankLabel = WmiUtil.getString(bankMap, PhysicalMemoryProperty.BANKLABEL, index);
                long capacity = WmiUtil.getUint64(bankMap, PhysicalMemoryProperty.CAPACITY, index);
                long speed = WmiUtil.getUint32(bankMap, PhysicalMemoryProperty.SPEED, index) * 1_000_000L;
                String manufacturer = WmiUtil.getString(bankMap, PhysicalMemoryProperty.MANUFACTURER, index);
                String memoryType = smBiosMemoryType(
                        WmiUtil.getUint32(bankMap, PhysicalMemoryProperty.SMBIOSMEMORYTYPE, index));
                physicalMemoryArray[index] = new PhysicalMemory(bankLabel, capacity, speed, manufacturer, memoryType);
            }
        } else {
            WmiQuery<PhysicalMemoryPropertyWin8> physicalMemoryQuery = new WmiQuery<>("Win32_PhysicalMemory",
                    PhysicalMemoryPropertyWin8.class);
            WmiResult<PhysicalMemoryPropertyWin8> bankMap = wmiQueryHandler.queryWMI(physicalMemoryQuery);

            physicalMemoryArray = new PhysicalMemory[bankMap.getResultCount()];
            for (int index = 0; index < bankMap.getResultCount(); index++) {
                String bankLabel = WmiUtil.getString(bankMap, PhysicalMemoryPropertyWin8.BANKLABEL, index);
                long capacity = WmiUtil.getUint64(bankMap, PhysicalMemoryPropertyWin8.CAPACITY, index);
                long speed = WmiUtil.getUint32(bankMap, PhysicalMemoryPropertyWin8.SPEED, index) * 1_000_000L;
                String manufacturer = WmiUtil.getString(bankMap, PhysicalMemoryPropertyWin8.MANUFACTURER, index);
                String memoryType = memoryType(
                        WmiUtil.getUint16(bankMap, PhysicalMemoryPropertyWin8.MEMORYTYPE, index));
                physicalMemoryArray[index] = new PhysicalMemory(bankLabel, capacity, speed, manufacturer, memoryType);
            }
        }
        return physicalMemoryArray;
    }

    /**
     * Convert memory type number to a human readable string
     *
     * @param type
     *            The memory type
     * @return A string describing the type
     */
    private static String memoryType(int type) {
        switch (type) {
        case 1:
            return "Other";
        case 2:
            return "DRAM";
        case 3:
            return "Synchronous DRAM";
        case 4:
            return "Cache DRAM";
        case 5:
            return "EDO";
        case 6:
            return "EDRAM";
        case 7:
            return "VRAM";
        case 8:
            return "SRAM";
        case 9:
            return "RAM";
        case 10:
            return "ROM";
        case 11:
            return "Flash";
        case 12:
            return "EEPROM";
        case 13:
            return "FEPROM";
        case 14:
            return "EPROM";
        case 15:
            return "CDRAM";
        case 16:
            return "3DRAM";
        case 17:
            return "SDRAM";
        case 18:
            return "SGRAM";
        case 19:
            return "RDRAM";
        case 20:
            return "DDR";
        case 21:
            return "DDR2";
        case 22:
            return "DDR2-FB-DIMM";
        case 24:
            return "DDR3";
        case 25:
            return "FBD2";
        default:
            return "Unknown";
        }
    }

    /**
     * Convert SMBIOS type number to a human readable string
     *
     * @param type
     *            The SMBIOS type
     * @return A string describing the type
     */
    private static String smBiosMemoryType(int type) {
        // https://www.dmtf.org/sites/default/files/standards/documents/DSP0134_3.2.0.pdf
        // table 76
        switch (type) {
        case 0x01:
            return "Other";
        case 0x03:
            return "DRAM";
        case 0x04:
            return "EDRAM";
        case 0x05:
            return "VRAM";
        case 0x06:
            return "SRAM";
        case 0x07:
            return "RAM";
        case 0x08:
            return "ROM";
        case 0x09:
            return "FLASH";
        case 0x0A:
            return "EEPROM";
        case 0x0B:
            return "FEPROM";
        case 0x0C:
            return "EPROM";
        case 0x0D:
            return "CDRAM";
        case 0x0E:
            return "3DRAM";
        case 0x0F:
            return "SDRAM";
        case 0x10:
            return "SGRAM";
        case 0x11:
            return "RDRAM";
        case 0x12:
            return "DDR";
        case 0x13:
            return "DDR2";
        case 0x14:
            return "DDR2 FB-DIMM";
        case 0x18:
            return "DDR3";
        case 0x19:
            return "FBD2";
        case 0x1A:
            return "DDR4";
        case 0x1B:
            return "LPDDR";
        case 0x1C:
            return "LPDDR2";
        case 0x1D:
            return "LPDDR3";
        case 0x1E:
            return "LPDDR4";
        case 0x1F:
            return "Logical non-volatile device";
        case 0x02:
        default:
            return "Unknown";
        }
    }

    private PerfInfo readPerfInfo() {
        long pageSize;
        long memAvailable;
        long memTotal;
        PERFORMANCE_INFORMATION performanceInfo = new PERFORMANCE_INFORMATION();
        if (!Psapi.INSTANCE.GetPerformanceInfo(performanceInfo, performanceInfo.size())) {
            LOG.error("Failed to get Performance Info. Error code: {}", Kernel32.INSTANCE.GetLastError());
            return new PerfInfo(0, 0, 4098);
        }
        pageSize = performanceInfo.PageSize.longValue();
        memAvailable = pageSize * performanceInfo.PhysicalAvailable.longValue();
        memTotal = pageSize * performanceInfo.PhysicalTotal.longValue();
        return new PerfInfo(memTotal, memAvailable, pageSize);
    }

    private static final class PerfInfo {
        private final long total;
        private final long available;
        private final long pageSize;

        private PerfInfo(long total, long available, long pageSize) {
            this.total = total;
            this.available = available;
            this.pageSize = pageSize;
        }
    }
}
