#!/bin/bash
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" | awk '
    BEGIN { total = 0 }
    NR == 1 { print $0 "\tCPU (System %)" }
    NR > 1 {
        val = $2; sub(/%/, "", val);
        sys_val = val / 8;
        total += sys_val;
        printf "%-20s %-10s %-20s \033[1;33m%6.2f%%\033[0m\n", $1, $2, $3 " " $4 " " $5, sys_val
    }
    END {
        printf "\n\033[1;32mTOTAL SYSTEM CPU LOAD: %6.2f%%\033[0m\n", total
    }'
