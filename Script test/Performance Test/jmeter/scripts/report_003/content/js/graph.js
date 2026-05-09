/*
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
$(document).ready(function() {

    $(".click-title").mouseenter( function(    e){
        e.preventDefault();
        this.style.cursor="pointer";
    });
    $(".click-title").mousedown( function(event){
        event.preventDefault();
    });

    // Ugly code while this script is shared among several pages
    try{
        refreshHitsPerSecond(true);
    } catch(e){}
    try{
        refreshResponseTimeOverTime(true);
    } catch(e){}
    try{
        refreshResponseTimePercentiles();
    } catch(e){}
});


var responseTimePercentilesInfos = {
        data: {"result": {"minY": 73.0, "minX": 0.0, "maxY": 8810.0, "series": [{"data": [[0.0, 73.0], [0.1, 93.0], [0.2, 140.0], [0.3, 140.0], [0.4, 146.0], [0.5, 172.0], [0.6, 192.0], [0.7, 194.0], [0.8, 195.0], [0.9, 197.0], [1.0, 199.0], [1.1, 200.0], [1.2, 201.0], [1.3, 201.0], [1.4, 203.0], [1.5, 203.0], [1.6, 221.0], [1.7, 234.0], [1.8, 234.0], [1.9, 236.0], [2.0, 260.0], [2.1, 264.0], [2.2, 297.0], [2.3, 297.0], [2.4, 300.0], [2.5, 301.0], [2.6, 302.0], [2.7, 303.0], [2.8, 316.0], [2.9, 327.0], [3.0, 328.0], [3.1, 331.0], [3.2, 331.0], [3.3, 334.0], [3.4, 337.0], [3.5, 337.0], [3.6, 340.0], [3.7, 342.0], [3.8, 344.0], [3.9, 350.0], [4.0, 353.0], [4.1, 356.0], [4.2, 363.0], [4.3, 367.0], [4.4, 369.0], [4.5, 370.0], [4.6, 370.0], [4.7, 385.0], [4.8, 386.0], [4.9, 389.0], [5.0, 395.0], [5.1, 396.0], [5.2, 396.0], [5.3, 398.0], [5.4, 398.0], [5.5, 398.0], [5.6, 399.0], [5.7, 399.0], [5.8, 399.0], [5.9, 399.0], [6.0, 399.0], [6.1, 400.0], [6.2, 401.0], [6.3, 401.0], [6.4, 402.0], [6.5, 402.0], [6.6, 403.0], [6.7, 403.0], [6.8, 403.0], [6.9, 405.0], [7.0, 411.0], [7.1, 429.0], [7.2, 431.0], [7.3, 432.0], [7.4, 432.0], [7.5, 435.0], [7.6, 436.0], [7.7, 436.0], [7.8, 438.0], [7.9, 448.0], [8.0, 452.0], [8.1, 456.0], [8.2, 457.0], [8.3, 458.0], [8.4, 460.0], [8.5, 462.0], [8.6, 469.0], [8.7, 482.0], [8.8, 496.0], [8.9, 496.0], [9.0, 497.0], [9.1, 498.0], [9.2, 498.0], [9.3, 498.0], [9.4, 498.0], [9.5, 498.0], [9.6, 498.0], [9.7, 499.0], [9.8, 499.0], [9.9, 499.0], [10.0, 500.0], [10.1, 500.0], [10.2, 500.0], [10.3, 501.0], [10.4, 502.0], [10.5, 502.0], [10.6, 506.0], [10.7, 508.0], [10.8, 514.0], [10.9, 516.0], [11.0, 525.0], [11.1, 530.0], [11.2, 534.0], [11.3, 537.0], [11.4, 540.0], [11.5, 541.0], [11.6, 549.0], [11.7, 553.0], [11.8, 560.0], [11.9, 561.0], [12.0, 568.0], [12.1, 568.0], [12.2, 569.0], [12.3, 576.0], [12.4, 594.0], [12.5, 594.0], [12.6, 595.0], [12.7, 595.0], [12.8, 595.0], [12.9, 596.0], [13.0, 596.0], [13.1, 596.0], [13.2, 597.0], [13.3, 598.0], [13.4, 598.0], [13.5, 598.0], [13.6, 599.0], [13.7, 599.0], [13.8, 599.0], [13.9, 600.0], [14.0, 600.0], [14.1, 600.0], [14.2, 600.0], [14.3, 600.0], [14.4, 600.0], [14.5, 600.0], [14.6, 600.0], [14.7, 601.0], [14.8, 602.0], [14.9, 603.0], [15.0, 603.0], [15.1, 603.0], [15.2, 603.0], [15.3, 605.0], [15.4, 609.0], [15.5, 637.0], [15.6, 643.0], [15.7, 648.0], [15.8, 660.0], [15.9, 661.0], [16.0, 670.0], [16.1, 695.0], [16.2, 697.0], [16.3, 698.0], [16.4, 698.0], [16.5, 699.0], [16.6, 699.0], [16.7, 700.0], [16.8, 701.0], [16.9, 702.0], [17.0, 704.0], [17.1, 704.0], [17.2, 704.0], [17.3, 714.0], [17.4, 715.0], [17.5, 730.0], [17.6, 736.0], [17.7, 750.0], [17.8, 759.0], [17.9, 759.0], [18.0, 768.0], [18.1, 770.0], [18.2, 789.0], [18.3, 792.0], [18.4, 796.0], [18.5, 796.0], [18.6, 796.0], [18.7, 798.0], [18.8, 799.0], [18.9, 799.0], [19.0, 800.0], [19.1, 801.0], [19.2, 802.0], [19.3, 802.0], [19.4, 830.0], [19.5, 847.0], [19.6, 862.0], [19.7, 894.0], [19.8, 897.0], [19.9, 899.0], [20.0, 900.0], [20.1, 901.0], [20.2, 903.0], [20.3, 904.0], [20.4, 944.0], [20.5, 946.0], [20.6, 952.0], [20.7, 960.0], [20.8, 994.0], [20.9, 995.0], [21.0, 996.0], [21.1, 998.0], [21.2, 999.0], [21.3, 1002.0], [21.4, 1002.0], [21.5, 1031.0], [21.6, 1051.0], [21.7, 1054.0], [21.8, 1070.0], [21.9, 1083.0], [22.0, 1096.0], [22.1, 1098.0], [22.2, 1099.0], [22.3, 1100.0], [22.4, 1100.0], [22.5, 1100.0], [22.6, 1100.0], [22.7, 1101.0], [22.8, 1101.0], [22.9, 1101.0], [23.0, 1109.0], [23.1, 1132.0], [23.2, 1135.0], [23.3, 1146.0], [23.4, 1152.0], [23.5, 1158.0], [23.6, 1169.0], [23.7, 1197.0], [23.8, 1197.0], [23.9, 1198.0], [24.0, 1198.0], [24.1, 1198.0], [24.2, 1199.0], [24.3, 1199.0], [24.4, 1199.0], [24.5, 1199.0], [24.6, 1200.0], [24.7, 1200.0], [24.8, 1201.0], [24.9, 1204.0], [25.0, 1207.0], [25.1, 1233.0], [25.2, 1244.0], [25.3, 1252.0], [25.4, 1260.0], [25.5, 1265.0], [25.6, 1269.0], [25.7, 1272.0], [25.8, 1275.0], [25.9, 1276.0], [26.0, 1277.0], [26.1, 1288.0], [26.2, 1294.0], [26.3, 1297.0], [26.4, 1297.0], [26.5, 1298.0], [26.6, 1300.0], [26.7, 1300.0], [26.8, 1301.0], [26.9, 1301.0], [27.0, 1301.0], [27.1, 1304.0], [27.2, 1309.0], [27.3, 1312.0], [27.4, 1319.0], [27.5, 1328.0], [27.6, 1329.0], [27.7, 1331.0], [27.8, 1332.0], [27.9, 1337.0], [28.0, 1344.0], [28.1, 1346.0], [28.2, 1347.0], [28.3, 1348.0], [28.4, 1348.0], [28.5, 1349.0], [28.6, 1350.0], [28.7, 1350.0], [28.8, 1351.0], [28.9, 1362.0], [29.0, 1365.0], [29.1, 1367.0], [29.2, 1371.0], [29.3, 1373.0], [29.4, 1380.0], [29.5, 1390.0], [29.6, 1396.0], [29.7, 1397.0], [29.8, 1397.0], [29.9, 1397.0], [30.0, 1398.0], [30.1, 1399.0], [30.2, 1399.0], [30.3, 1400.0], [30.4, 1400.0], [30.5, 1401.0], [30.6, 1401.0], [30.7, 1402.0], [30.8, 1402.0], [30.9, 1402.0], [31.0, 1403.0], [31.1, 1409.0], [31.2, 1409.0], [31.3, 1409.0], [31.4, 1409.0], [31.5, 1421.0], [31.6, 1429.0], [31.7, 1431.0], [31.8, 1431.0], [31.9, 1434.0], [32.0, 1438.0], [32.1, 1438.0], [32.2, 1439.0], [32.3, 1443.0], [32.4, 1445.0], [32.5, 1447.0], [32.6, 1448.0], [32.7, 1449.0], [32.8, 1450.0], [32.9, 1451.0], [33.0, 1453.0], [33.1, 1453.0], [33.2, 1455.0], [33.3, 1455.0], [33.4, 1455.0], [33.5, 1455.0], [33.6, 1456.0], [33.7, 1456.0], [33.8, 1458.0], [33.9, 1458.0], [34.0, 1459.0], [34.1, 1468.0], [34.2, 1469.0], [34.3, 1476.0], [34.4, 1478.0], [34.5, 1487.0], [34.6, 1492.0], [34.7, 1494.0], [34.8, 1497.0], [34.9, 1497.0], [35.0, 1498.0], [35.1, 1498.0], [35.2, 1498.0], [35.3, 1499.0], [35.4, 1499.0], [35.5, 1499.0], [35.6, 1499.0], [35.7, 1499.0], [35.8, 1500.0], [35.9, 1500.0], [36.0, 1500.0], [36.1, 1501.0], [36.2, 1501.0], [36.3, 1501.0], [36.4, 1501.0], [36.5, 1501.0], [36.6, 1502.0], [36.7, 1503.0], [36.8, 1512.0], [36.9, 1512.0], [37.0, 1517.0], [37.1, 1518.0], [37.2, 1521.0], [37.3, 1525.0], [37.4, 1529.0], [37.5, 1530.0], [37.6, 1530.0], [37.7, 1531.0], [37.8, 1531.0], [37.9, 1532.0], [38.0, 1532.0], [38.1, 1532.0], [38.2, 1534.0], [38.3, 1534.0], [38.4, 1536.0], [38.5, 1537.0], [38.6, 1537.0], [38.7, 1545.0], [38.8, 1546.0], [38.9, 1546.0], [39.0, 1547.0], [39.1, 1547.0], [39.2, 1551.0], [39.3, 1552.0], [39.4, 1552.0], [39.5, 1553.0], [39.6, 1553.0], [39.7, 1557.0], [39.8, 1558.0], [39.9, 1559.0], [40.0, 1559.0], [40.1, 1560.0], [40.2, 1561.0], [40.3, 1562.0], [40.4, 1562.0], [40.5, 1564.0], [40.6, 1566.0], [40.7, 1568.0], [40.8, 1568.0], [40.9, 1569.0], [41.0, 1578.0], [41.1, 1588.0], [41.2, 1588.0], [41.3, 1592.0], [41.4, 1592.0], [41.5, 1595.0], [41.6, 1596.0], [41.7, 1596.0], [41.8, 1596.0], [41.9, 1597.0], [42.0, 1597.0], [42.1, 1597.0], [42.2, 1597.0], [42.3, 1598.0], [42.4, 1598.0], [42.5, 1598.0], [42.6, 1598.0], [42.7, 1598.0], [42.8, 1599.0], [42.9, 1599.0], [43.0, 1599.0], [43.1, 1600.0], [43.2, 1600.0], [43.3, 1600.0], [43.4, 1600.0], [43.5, 1601.0], [43.6, 1601.0], [43.7, 1601.0], [43.8, 1601.0], [43.9, 1601.0], [44.0, 1601.0], [44.1, 1602.0], [44.2, 1602.0], [44.3, 1603.0], [44.4, 1604.0], [44.5, 1606.0], [44.6, 1611.0], [44.7, 1627.0], [44.8, 1629.0], [44.9, 1631.0], [45.0, 1636.0], [45.1, 1640.0], [45.2, 1643.0], [45.3, 1643.0], [45.4, 1643.0], [45.5, 1646.0], [45.6, 1647.0], [45.7, 1648.0], [45.8, 1649.0], [45.9, 1650.0], [46.0, 1651.0], [46.1, 1652.0], [46.2, 1652.0], [46.3, 1652.0], [46.4, 1652.0], [46.5, 1652.0], [46.6, 1652.0], [46.7, 1654.0], [46.8, 1656.0], [46.9, 1656.0], [47.0, 1657.0], [47.1, 1661.0], [47.2, 1662.0], [47.3, 1663.0], [47.4, 1667.0], [47.5, 1667.0], [47.6, 1668.0], [47.7, 1696.0], [47.8, 1697.0], [47.9, 1697.0], [48.0, 1697.0], [48.1, 1698.0], [48.2, 1698.0], [48.3, 1699.0], [48.4, 1699.0], [48.5, 1699.0], [48.6, 1699.0], [48.7, 1699.0], [48.8, 1700.0], [48.9, 1700.0], [49.0, 1700.0], [49.1, 1700.0], [49.2, 1700.0], [49.3, 1700.0], [49.4, 1700.0], [49.5, 1700.0], [49.6, 1701.0], [49.7, 1701.0], [49.8, 1701.0], [49.9, 1701.0], [50.0, 1701.0], [50.1, 1702.0], [50.2, 1703.0], [50.3, 1703.0], [50.4, 1705.0], [50.5, 1706.0], [50.6, 1711.0], [50.7, 1720.0], [50.8, 1723.0], [50.9, 1723.0], [51.0, 1730.0], [51.1, 1731.0], [51.2, 1734.0], [51.3, 1735.0], [51.4, 1737.0], [51.5, 1742.0], [51.6, 1742.0], [51.7, 1743.0], [51.8, 1743.0], [51.9, 1745.0], [52.0, 1751.0], [52.1, 1754.0], [52.2, 1761.0], [52.3, 1763.0], [52.4, 1764.0], [52.5, 1764.0], [52.6, 1764.0], [52.7, 1778.0], [52.8, 1784.0], [52.9, 1789.0], [53.0, 1792.0], [53.1, 1796.0], [53.2, 1797.0], [53.3, 1797.0], [53.4, 1797.0], [53.5, 1798.0], [53.6, 1798.0], [53.7, 1799.0], [53.8, 1799.0], [53.9, 1799.0], [54.0, 1799.0], [54.1, 1800.0], [54.2, 1800.0], [54.3, 1800.0], [54.4, 1801.0], [54.5, 1801.0], [54.6, 1802.0], [54.7, 1802.0], [54.8, 1804.0], [54.9, 1817.0], [55.0, 1829.0], [55.1, 1831.0], [55.2, 1836.0], [55.3, 1837.0], [55.4, 1837.0], [55.5, 1847.0], [55.6, 1848.0], [55.7, 1852.0], [55.8, 1857.0], [55.9, 1862.0], [56.0, 1865.0], [56.1, 1868.0], [56.2, 1870.0], [56.3, 1873.0], [56.4, 1891.0], [56.5, 1896.0], [56.6, 1897.0], [56.7, 1897.0], [56.8, 1898.0], [56.9, 1898.0], [57.0, 1898.0], [57.1, 1898.0], [57.2, 1898.0], [57.3, 1898.0], [57.4, 1898.0], [57.5, 1899.0], [57.6, 1899.0], [57.7, 1899.0], [57.8, 1900.0], [57.9, 1900.0], [58.0, 1900.0], [58.1, 1900.0], [58.2, 1900.0], [58.3, 1901.0], [58.4, 1901.0], [58.5, 1901.0], [58.6, 1902.0], [58.7, 1903.0], [58.8, 1906.0], [58.9, 1914.0], [59.0, 1929.0], [59.1, 1930.0], [59.2, 1934.0], [59.3, 1934.0], [59.4, 1943.0], [59.5, 1956.0], [59.6, 1965.0], [59.7, 1966.0], [59.8, 1996.0], [59.9, 1996.0], [60.0, 1998.0], [60.1, 1998.0], [60.2, 1998.0], [60.3, 1999.0], [60.4, 1999.0], [60.5, 2000.0], [60.6, 2000.0], [60.7, 2000.0], [60.8, 2000.0], [60.9, 2001.0], [61.0, 2002.0], [61.1, 2003.0], [61.2, 2003.0], [61.3, 2006.0], [61.4, 2026.0], [61.5, 2036.0], [61.6, 2039.0], [61.7, 2042.0], [61.8, 2050.0], [61.9, 2064.0], [62.0, 2095.0], [62.1, 2095.0], [62.2, 2097.0], [62.3, 2097.0], [62.4, 2098.0], [62.5, 2098.0], [62.6, 2098.0], [62.7, 2098.0], [62.8, 2099.0], [62.9, 2099.0], [63.0, 2099.0], [63.1, 2100.0], [63.2, 2101.0], [63.3, 2101.0], [63.4, 2102.0], [63.5, 2102.0], [63.6, 2102.0], [63.7, 2103.0], [63.8, 2103.0], [63.9, 2104.0], [64.0, 2105.0], [64.1, 2126.0], [64.2, 2128.0], [64.3, 2130.0], [64.4, 2144.0], [64.5, 2144.0], [64.6, 2155.0], [64.7, 2166.0], [64.8, 2189.0], [64.9, 2194.0], [65.0, 2195.0], [65.1, 2195.0], [65.2, 2196.0], [65.3, 2196.0], [65.4, 2196.0], [65.5, 2197.0], [65.6, 2198.0], [65.7, 2199.0], [65.8, 2199.0], [65.9, 2199.0], [66.0, 2200.0], [66.1, 2200.0], [66.2, 2200.0], [66.3, 2200.0], [66.4, 2200.0], [66.5, 2201.0], [66.6, 2201.0], [66.7, 2202.0], [66.8, 2203.0], [66.9, 2204.0], [67.0, 2204.0], [67.1, 2206.0], [67.2, 2226.0], [67.3, 2234.0], [67.4, 2239.0], [67.5, 2241.0], [67.6, 2257.0], [67.7, 2261.0], [67.8, 2271.0], [67.9, 2272.0], [68.0, 2295.0], [68.1, 2295.0], [68.2, 2296.0], [68.3, 2296.0], [68.4, 2296.0], [68.5, 2296.0], [68.6, 2296.0], [68.7, 2298.0], [68.8, 2298.0], [68.9, 2298.0], [69.0, 2298.0], [69.1, 2298.0], [69.2, 2301.0], [69.3, 2302.0], [69.4, 2327.0], [69.5, 2335.0], [69.6, 2343.0], [69.7, 2356.0], [69.8, 2360.0], [69.9, 2377.0], [70.0, 2394.0], [70.1, 2395.0], [70.2, 2396.0], [70.3, 2398.0], [70.4, 2398.0], [70.5, 2398.0], [70.6, 2400.0], [70.7, 2400.0], [70.8, 2402.0], [70.9, 2403.0], [71.0, 2430.0], [71.1, 2436.0], [71.2, 2438.0], [71.3, 2439.0], [71.4, 2440.0], [71.5, 2447.0], [71.6, 2448.0], [71.7, 2449.0], [71.8, 2456.0], [71.9, 2467.0], [72.0, 2483.0], [72.1, 2484.0], [72.2, 2491.0], [72.3, 2497.0], [72.4, 2497.0], [72.5, 2497.0], [72.6, 2498.0], [72.7, 2500.0], [72.8, 2501.0], [72.9, 2502.0], [73.0, 2503.0], [73.1, 2503.0], [73.2, 2504.0], [73.3, 2523.0], [73.4, 2531.0], [73.5, 2533.0], [73.6, 2537.0], [73.7, 2542.0], [73.8, 2569.0], [73.9, 2573.0], [74.0, 2595.0], [74.1, 2597.0], [74.2, 2597.0], [74.3, 2598.0], [74.4, 2599.0], [74.5, 2599.0], [74.6, 2600.0], [74.7, 2600.0], [74.8, 2600.0], [74.9, 2604.0], [75.0, 2604.0], [75.1, 2626.0], [75.2, 2644.0], [75.3, 2665.0], [75.4, 2696.0], [75.5, 2698.0], [75.6, 2699.0], [75.7, 2699.0], [75.8, 2699.0], [75.9, 2700.0], [76.0, 2705.0], [76.1, 2734.0], [76.2, 2747.0], [76.3, 2748.0], [76.4, 2750.0], [76.5, 2752.0], [76.6, 2755.0], [76.7, 2756.0], [76.8, 2794.0], [76.9, 2795.0], [77.0, 2797.0], [77.1, 2797.0], [77.2, 2799.0], [77.3, 2799.0], [77.4, 2799.0], [77.5, 2799.0], [77.6, 2800.0], [77.7, 2801.0], [77.8, 2801.0], [77.9, 2801.0], [78.0, 2802.0], [78.1, 2802.0], [78.2, 2807.0], [78.3, 2826.0], [78.4, 2831.0], [78.5, 2847.0], [78.6, 2847.0], [78.7, 2857.0], [78.8, 2866.0], [78.9, 2869.0], [79.0, 2885.0], [79.1, 2895.0], [79.2, 2897.0], [79.3, 2899.0], [79.4, 2899.0], [79.5, 2900.0], [79.6, 2901.0], [79.7, 2901.0], [79.8, 2901.0], [79.9, 2907.0], [80.0, 2907.0], [80.1, 2916.0], [80.2, 2931.0], [80.3, 2946.0], [80.4, 2955.0], [80.5, 2955.0], [80.6, 2994.0], [80.7, 2998.0], [80.8, 3000.0], [80.9, 3001.0], [81.0, 3001.0], [81.1, 3002.0], [81.2, 3048.0], [81.3, 3065.0], [81.4, 3067.0], [81.5, 3091.0], [81.6, 3095.0], [81.7, 3096.0], [81.8, 3097.0], [81.9, 3098.0], [82.0, 3098.0], [82.1, 3099.0], [82.2, 3100.0], [82.3, 3102.0], [82.4, 3107.0], [82.5, 3111.0], [82.6, 3138.0], [82.7, 3141.0], [82.8, 3142.0], [82.9, 3143.0], [83.0, 3165.0], [83.1, 3176.0], [83.2, 3196.0], [83.3, 3196.0], [83.4, 3200.0], [83.5, 3201.0], [83.6, 3204.0], [83.7, 3206.0], [83.8, 3207.0], [83.9, 3211.0], [84.0, 3238.0], [84.1, 3245.0], [84.2, 3274.0], [84.3, 3292.0], [84.4, 3296.0], [84.5, 3298.0], [84.6, 3299.0], [84.7, 3300.0], [84.8, 3300.0], [84.9, 3305.0], [85.0, 3305.0], [85.1, 3306.0], [85.2, 3307.0], [85.3, 3309.0], [85.4, 3329.0], [85.5, 3368.0], [85.6, 3395.0], [85.7, 3397.0], [85.8, 3399.0], [85.9, 3400.0], [86.0, 3405.0], [86.1, 3447.0], [86.2, 3460.0], [86.3, 3494.0], [86.4, 3495.0], [86.5, 3495.0], [86.6, 3497.0], [86.7, 3499.0], [86.8, 3500.0], [86.9, 3500.0], [87.0, 3504.0], [87.1, 3506.0], [87.2, 3506.0], [87.3, 3507.0], [87.4, 3530.0], [87.5, 3551.0], [87.6, 3560.0], [87.7, 3565.0], [87.8, 3593.0], [87.9, 3594.0], [88.0, 3594.0], [88.1, 3597.0], [88.2, 3598.0], [88.3, 3598.0], [88.4, 3599.0], [88.5, 3600.0], [88.6, 3601.0], [88.7, 3604.0], [88.8, 3606.0], [88.9, 3606.0], [89.0, 3607.0], [89.1, 3609.0], [89.2, 3696.0], [89.3, 3701.0], [89.4, 3704.0], [89.5, 3705.0], [89.6, 3708.0], [89.7, 3728.0], [89.8, 3731.0], [89.9, 3739.0], [90.0, 3739.0], [90.1, 3743.0], [90.2, 3783.0], [90.3, 3795.0], [90.4, 3797.0], [90.5, 3799.0], [90.6, 3800.0], [90.7, 3806.0], [90.8, 3823.0], [90.9, 3835.0], [91.0, 3855.0], [91.1, 3862.0], [91.2, 3896.0], [91.3, 3896.0], [91.4, 3898.0], [91.5, 3899.0], [91.6, 3901.0], [91.7, 3902.0], [91.8, 3907.0], [91.9, 3922.0], [92.0, 3938.0], [92.1, 3946.0], [92.2, 3959.0], [92.3, 3976.0], [92.4, 3992.0], [92.5, 3998.0], [92.6, 3998.0], [92.7, 3998.0], [92.8, 3998.0], [92.9, 4000.0], [93.0, 4003.0], [93.1, 4094.0], [93.2, 4097.0], [93.3, 4097.0], [93.4, 4099.0], [93.5, 4100.0], [93.6, 4103.0], [93.7, 4122.0], [93.8, 4130.0], [93.9, 4165.0], [94.0, 4197.0], [94.1, 4198.0], [94.2, 4199.0], [94.3, 4201.0], [94.4, 4202.0], [94.5, 4203.0], [94.6, 4206.0], [94.7, 4296.0], [94.8, 4300.0], [94.9, 4300.0], [95.0, 4308.0], [95.1, 4311.0], [95.2, 4399.0], [95.3, 4404.0], [95.4, 4407.0], [95.5, 4449.0], [95.6, 4496.0], [95.7, 4498.0], [95.8, 4499.0], [95.9, 4500.0], [96.0, 4600.0], [96.1, 4604.0], [96.2, 4703.0], [96.3, 4799.0], [96.4, 4801.0], [96.5, 4855.0], [96.6, 4880.0], [96.7, 4908.0], [96.8, 5095.0], [96.9, 5098.0], [97.0, 5209.0], [97.1, 5298.0], [97.2, 5302.0], [97.3, 5599.0], [97.4, 5701.0], [97.5, 5701.0], [97.6, 5703.0], [97.7, 5858.0], [97.8, 5865.0], [97.9, 5995.0], [98.0, 6195.0], [98.1, 6200.0], [98.2, 6296.0], [98.3, 6399.0], [98.4, 6501.0], [98.5, 6505.0], [98.6, 6513.0], [98.7, 6594.0], [98.8, 7007.0], [98.9, 7012.0], [99.0, 7100.0], [99.1, 7136.0], [99.2, 7168.0], [99.3, 7277.0], [99.4, 7399.0], [99.5, 7409.0], [99.6, 7507.0], [99.7, 7543.0], [99.8, 8804.0], [99.9, 8810.0]], "isOverall": false, "label": "GET_FilterCandidates", "isController": false}], "supportsControllersDiscrimination": true, "maxX": 100.0, "title": "Response Time Percentiles"}},
        getOptions: function() {
            return {
                series: {
                    points: { show: false }
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendResponseTimePercentiles'
                },
                xaxis: {
                    tickDecimals: 1,
                    axisLabel: "Percentiles",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Percentile value in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : %x.2 percentile was %y ms"
                },
                selection: { mode: "xy" },
            };
        },
        createGraph: function() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesResponseTimePercentiles"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotResponseTimesPercentiles"), dataset, options);
            // setup overview
            $.plot($("#overviewResponseTimesPercentiles"), dataset, prepareOverviewOptions(options));
        }
};

/**
 * @param elementId Id of element where we display message
 */
function setEmptyGraph(elementId) {
    $(function() {
        $(elementId).text("No graph series with filter="+seriesFilter);
    });
}

// Response times percentiles
function refreshResponseTimePercentiles() {
    var infos = responseTimePercentilesInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyResponseTimePercentiles");
        return;
    }
    if (isGraph($("#flotResponseTimesPercentiles"))){
        infos.createGraph();
    } else {
        var choiceContainer = $("#choicesResponseTimePercentiles");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotResponseTimesPercentiles", "#overviewResponseTimesPercentiles");
        $('#bodyResponseTimePercentiles .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
}

var responseTimeDistributionInfos = {
        data: {"result": {"minY": 1.0, "minX": 0.0, "maxY": 73.0, "series": [{"data": [[0.0, 2.0], [600.0, 28.0], [700.0, 22.0], [800.0, 10.0], [900.0, 13.0], [1000.0, 10.0], [1100.0, 23.0], [1200.0, 20.0], [1300.0, 37.0], [1400.0, 55.0], [1500.0, 73.0], [1600.0, 57.0], [1700.0, 53.0], [1800.0, 37.0], [1900.0, 27.0], [2000.0, 26.0], [2100.0, 29.0], [2300.0, 14.0], [2200.0, 32.0], [2400.0, 21.0], [2500.0, 19.0], [2600.0, 14.0], [2800.0, 19.0], [2700.0, 17.0], [2900.0, 13.0], [3000.0, 14.0], [3100.0, 12.0], [3300.0, 12.0], [3200.0, 13.0], [3400.0, 9.0], [3500.0, 17.0], [3600.0, 8.0], [3700.0, 13.0], [3800.0, 10.0], [3900.0, 13.0], [4000.0, 6.0], [4300.0, 5.0], [4200.0, 5.0], [4100.0, 8.0], [4600.0, 2.0], [4400.0, 6.0], [4500.0, 1.0], [4800.0, 3.0], [4700.0, 2.0], [4900.0, 1.0], [5000.0, 2.0], [5200.0, 2.0], [5300.0, 1.0], [5500.0, 1.0], [5700.0, 3.0], [5800.0, 2.0], [5900.0, 1.0], [6100.0, 1.0], [6300.0, 1.0], [6200.0, 2.0], [6500.0, 4.0], [7000.0, 2.0], [7100.0, 3.0], [7200.0, 1.0], [7400.0, 1.0], [7300.0, 1.0], [7500.0, 2.0], [8800.0, 2.0], [100.0, 9.0], [200.0, 12.0], [300.0, 38.0], [400.0, 39.0], [500.0, 39.0]], "isOverall": false, "label": "GET_FilterCandidates", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 100, "maxX": 8800.0, "title": "Response Time Distribution"}},
        getOptions: function() {
            var granularity = this.data.result.granularity;
            return {
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendResponseTimeDistribution'
                },
                xaxis:{
                    axisLabel: "Response times in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of responses",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                bars : {
                    show: true,
                    barWidth: this.data.result.granularity
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: function(label, xval, yval, flotItem){
                        return yval + " responses for " + label + " were between " + xval + " and " + (xval + granularity) + " ms";
                    }
                }
            };
        },
        createGraph: function() {
            var data = this.data;
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotResponseTimeDistribution"), prepareData(data.result.series, $("#choicesResponseTimeDistribution")), options);
        }

};

// Response time distribution
function refreshResponseTimeDistribution() {
    var infos = responseTimeDistributionInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyResponseTimeDistribution");
        return;
    }
    if (isGraph($("#flotResponseTimeDistribution"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesResponseTimeDistribution");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        $('#footerResponseTimeDistribution .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};


var syntheticResponseTimeDistributionInfos = {
        data: {"result": {"minY": 103.0, "minX": 0.0, "ticks": [[0, "Requests having \nresponse time <= 500ms"], [1, "Requests having \nresponse time > 500ms and <= 1,500ms"], [2, "Requests having \nresponse time > 1,500ms"], [3, "Requests in error"]], "maxY": 640.0, "series": [{"data": [[0.0, 103.0]], "color": "#9ACD32", "isOverall": false, "label": "Requests having \nresponse time <= 500ms", "isController": false}, {"data": [[1.0, 257.0]], "color": "yellow", "isOverall": false, "label": "Requests having \nresponse time > 500ms and <= 1,500ms", "isController": false}, {"data": [[2.0, 640.0]], "color": "orange", "isOverall": false, "label": "Requests having \nresponse time > 1,500ms", "isController": false}, {"data": [], "color": "#FF6347", "isOverall": false, "label": "Requests in error", "isController": false}], "supportsControllersDiscrimination": false, "maxX": 2.0, "title": "Synthetic Response Times Distribution"}},
        getOptions: function() {
            return {
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendSyntheticResponseTimeDistribution'
                },
                xaxis:{
                    axisLabel: "Response times ranges",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                    tickLength:0,
                    min:-0.5,
                    max:3.5
                },
                yaxis: {
                    axisLabel: "Number of responses",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                bars : {
                    show: true,
                    align: "center",
                    barWidth: 0.25,
                    fill:.75
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: function(label, xval, yval, flotItem){
                        return yval + " " + label;
                    }
                }
            };
        },
        createGraph: function() {
            var data = this.data;
            var options = this.getOptions();
            prepareOptions(options, data);
            options.xaxis.ticks = data.result.ticks;
            $.plot($("#flotSyntheticResponseTimeDistribution"), prepareData(data.result.series, $("#choicesSyntheticResponseTimeDistribution")), options);
        }

};

// Response time distribution
function refreshSyntheticResponseTimeDistribution() {
    var infos = syntheticResponseTimeDistributionInfos;
    prepareSeries(infos.data, true);
    if (isGraph($("#flotSyntheticResponseTimeDistribution"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesSyntheticResponseTimeDistribution");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        $('#footerSyntheticResponseTimeDistribution .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var activeThreadsOverTimeInfos = {
        data: {"result": {"minY": 46.32299999999997, "minX": 1.77834756E12, "maxY": 46.32299999999997, "series": [{"data": [[1.77834756E12, 46.32299999999997]], "isOverall": false, "label": "Standard Load Test (50 HRs)", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.77834756E12, "title": "Active Threads Over Time"}},
        getOptions: function() {
            return {
                series: {
                    stack: true,
                    lines: {
                        show: true,
                        fill: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of active threads",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                legend: {
                    noColumns: 6,
                    show: true,
                    container: '#legendActiveThreadsOverTime'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                selection: {
                    mode: 'xy'
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : At %x there were %y active threads"
                }
            };
        },
        createGraph: function() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesActiveThreadsOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotActiveThreadsOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewActiveThreadsOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Active Threads Over Time
function refreshActiveThreadsOverTime(fixTimestamps) {
    var infos = activeThreadsOverTimeInfos;
    prepareSeries(infos.data);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 25200000);
    }
    if(isGraph($("#flotActiveThreadsOverTime"))) {
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesActiveThreadsOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotActiveThreadsOverTime", "#overviewActiveThreadsOverTime");
        $('#footerActiveThreadsOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var timeVsThreadsInfos = {
        data: {"result": {"minY": 143.5, "minX": 1.0, "maxY": 2992.6666666666665, "series": [{"data": [[33.0, 1857.8], [32.0, 1163.0], [2.0, 234.0], [35.0, 1399.6666666666667], [34.0, 1454.3333333333333], [37.0, 1642.142857142857], [36.0, 2214.6666666666665], [39.0, 1767.4444444444443], [38.0, 2470.6666666666665], [41.0, 2602.0], [40.0, 1230.4], [42.0, 2992.6666666666665], [43.0, 2685.166666666667], [44.0, 1969.5], [45.0, 1431.1666666666667], [47.0, 1649.0], [46.0, 1506.0], [48.0, 1568.116504854369], [49.0, 1717.0], [50.0, 2265.6497175141244], [5.0, 170.0], [6.0, 369.3333333333333], [7.0, 195.0], [9.0, 461.5], [10.0, 297.0], [11.0, 322.5], [12.0, 703.3333333333334], [14.0, 904.0], [15.0, 668.6666666666666], [16.0, 1122.6666666666667], [1.0, 143.5], [17.0, 1465.6666666666667], [19.0, 719.0], [20.0, 883.0], [22.0, 1561.0], [23.0, 1161.5], [25.0, 847.5], [26.0, 1097.111111111111], [27.0, 841.75], [29.0, 1114.0], [30.0, 1412.6], [31.0, 1054.0]], "isOverall": false, "label": "GET_FilterCandidates", "isController": false}, {"data": [[46.32299999999997, 2021.4710000000023]], "isOverall": false, "label": "GET_FilterCandidates-Aggregated", "isController": false}], "supportsControllersDiscrimination": true, "maxX": 50.0, "title": "Time VS Threads"}},
        getOptions: function() {
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    axisLabel: "Number of active threads",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Average response times in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                legend: { noColumns: 2,show: true, container: '#legendTimeVsThreads' },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s: At %x.2 active threads, Average response time was %y.2 ms"
                }
            };
        },
        createGraph: function() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesTimeVsThreads"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotTimesVsThreads"), dataset, options);
            // setup overview
            $.plot($("#overviewTimesVsThreads"), dataset, prepareOverviewOptions(options));
        }
};

// Time vs threads
function refreshTimeVsThreads(){
    var infos = timeVsThreadsInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyTimeVsThreads");
        return;
    }
    if(isGraph($("#flotTimesVsThreads"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesTimeVsThreads");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotTimesVsThreads", "#overviewTimesVsThreads");
        $('#footerTimeVsThreads .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var bytesThroughputOverTimeInfos = {
        data : {"result": {"minY": 3716.6666666666665, "minX": 1.77834756E12, "maxY": 453460.8, "series": [{"data": [[1.77834756E12, 453460.8]], "isOverall": false, "label": "Bytes received per second", "isController": false}, {"data": [[1.77834756E12, 3716.6666666666665]], "isOverall": false, "label": "Bytes sent per second", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.77834756E12, "title": "Bytes Throughput Over Time"}},
        getOptions : function(){
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity) ,
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Bytes / sec",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendBytesThroughputOverTime'
                },
                selection: {
                    mode: "xy"
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s at %x was %y"
                }
            };
        },
        createGraph : function() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesBytesThroughputOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotBytesThroughputOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewBytesThroughputOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Bytes throughput Over Time
function refreshBytesThroughputOverTime(fixTimestamps) {
    var infos = bytesThroughputOverTimeInfos;
    prepareSeries(infos.data);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 25200000);
    }
    if(isGraph($("#flotBytesThroughputOverTime"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesBytesThroughputOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotBytesThroughputOverTime", "#overviewBytesThroughputOverTime");
        $('#footerBytesThroughputOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
}

var responseTimesOverTimeInfos = {
        data: {"result": {"minY": 2021.4710000000023, "minX": 1.77834756E12, "maxY": 2021.4710000000023, "series": [{"data": [[1.77834756E12, 2021.4710000000023]], "isOverall": false, "label": "GET_FilterCandidates", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.77834756E12, "title": "Response Time Over Time"}},
        getOptions: function(){
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Average response time in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendResponseTimesOverTime'
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : at %x Average response time was %y ms"
                }
            };
        },
        createGraph: function() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesResponseTimesOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotResponseTimesOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewResponseTimesOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Response Times Over Time
function refreshResponseTimeOverTime(fixTimestamps) {
    var infos = responseTimesOverTimeInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyResponseTimeOverTime");
        return;
    }
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 25200000);
    }
    if(isGraph($("#flotResponseTimesOverTime"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesResponseTimesOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotResponseTimesOverTime", "#overviewResponseTimesOverTime");
        $('#footerResponseTimesOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var latenciesOverTimeInfos = {
        data: {"result": {"minY": 2003.2109999999984, "minX": 1.77834756E12, "maxY": 2003.2109999999984, "series": [{"data": [[1.77834756E12, 2003.2109999999984]], "isOverall": false, "label": "GET_FilterCandidates", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.77834756E12, "title": "Latencies Over Time"}},
        getOptions: function() {
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Average response latencies in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendLatenciesOverTime'
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : at %x Average latency was %y ms"
                }
            };
        },
        createGraph: function () {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesLatenciesOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotLatenciesOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewLatenciesOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Latencies Over Time
function refreshLatenciesOverTime(fixTimestamps) {
    var infos = latenciesOverTimeInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyLatenciesOverTime");
        return;
    }
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 25200000);
    }
    if(isGraph($("#flotLatenciesOverTime"))) {
        infos.createGraph();
    }else {
        var choiceContainer = $("#choicesLatenciesOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotLatenciesOverTime", "#overviewLatenciesOverTime");
        $('#footerLatenciesOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var connectTimeOverTimeInfos = {
        data: {"result": {"minY": 0.915, "minX": 1.77834756E12, "maxY": 0.915, "series": [{"data": [[1.77834756E12, 0.915]], "isOverall": false, "label": "GET_FilterCandidates", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.77834756E12, "title": "Connect Time Over Time"}},
        getOptions: function() {
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getConnectTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Average Connect Time in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendConnectTimeOverTime'
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : at %x Average connect time was %y ms"
                }
            };
        },
        createGraph: function () {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesConnectTimeOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotConnectTimeOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewConnectTimeOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Connect Time Over Time
function refreshConnectTimeOverTime(fixTimestamps) {
    var infos = connectTimeOverTimeInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyConnectTimeOverTime");
        return;
    }
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 25200000);
    }
    if(isGraph($("#flotConnectTimeOverTime"))) {
        infos.createGraph();
    }else {
        var choiceContainer = $("#choicesConnectTimeOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotConnectTimeOverTime", "#overviewConnectTimeOverTime");
        $('#footerConnectTimeOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var responseTimePercentilesOverTimeInfos = {
        data: {"result": {"minY": 73.0, "minX": 1.77834756E12, "maxY": 8810.0, "series": [{"data": [[1.77834756E12, 8810.0]], "isOverall": false, "label": "Max", "isController": false}, {"data": [[1.77834756E12, 73.0]], "isOverall": false, "label": "Min", "isController": false}, {"data": [[1.77834756E12, 3739.0]], "isOverall": false, "label": "90th percentile", "isController": false}, {"data": [[1.77834756E12, 7099.120000000001]], "isOverall": false, "label": "99th percentile", "isController": false}, {"data": [[1.77834756E12, 1701.5]], "isOverall": false, "label": "Median", "isController": false}, {"data": [[1.77834756E12, 4307.599999999999]], "isOverall": false, "label": "95th percentile", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.77834756E12, "title": "Response Time Percentiles Over Time (successful requests only)"}},
        getOptions: function() {
            return {
                series: {
                    lines: {
                        show: true,
                        fill: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Response Time in ms",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: '#legendResponseTimePercentilesOverTime'
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s : at %x Response time was %y ms"
                }
            };
        },
        createGraph: function () {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesResponseTimePercentilesOverTime"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotResponseTimePercentilesOverTime"), dataset, options);
            // setup overview
            $.plot($("#overviewResponseTimePercentilesOverTime"), dataset, prepareOverviewOptions(options));
        }
};

// Response Time Percentiles Over Time
function refreshResponseTimePercentilesOverTime(fixTimestamps) {
    var infos = responseTimePercentilesOverTimeInfos;
    prepareSeries(infos.data);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 25200000);
    }
    if(isGraph($("#flotResponseTimePercentilesOverTime"))) {
        infos.createGraph();
    }else {
        var choiceContainer = $("#choicesResponseTimePercentilesOverTime");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotResponseTimePercentilesOverTime", "#overviewResponseTimePercentilesOverTime");
        $('#footerResponseTimePercentilesOverTime .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};


var responseTimeVsRequestInfos = {
    data: {"result": {"minY": 747.5, "minX": 11.0, "maxY": 4000.0, "series": [{"data": [[32.0, 1455.0], [33.0, 1409.0], [35.0, 1501.0], [11.0, 3497.0], [12.0, 3157.0], [13.0, 3750.5], [14.0, 747.5], [15.0, 4000.0], [17.0, 3298.0], [19.0, 2200.5], [20.0, 2794.5], [21.0, 2196.0], [22.0, 2201.5], [23.0, 2191.5], [24.0, 2020.5], [26.0, 1798.0], [27.0, 1667.0], [28.0, 1710.0], [29.0, 1595.5], [30.0, 1596.5], [31.0, 1697.0]], "isOverall": false, "label": "Successes", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 1000, "maxX": 35.0, "title": "Response Time Vs Request"}},
    getOptions: function() {
        return {
            series: {
                lines: {
                    show: false
                },
                points: {
                    show: true
                }
            },
            xaxis: {
                axisLabel: "Global number of requests per second",
                axisLabelUseCanvas: true,
                axisLabelFontSizePixels: 12,
                axisLabelFontFamily: 'Verdana, Arial',
                axisLabelPadding: 20,
            },
            yaxis: {
                axisLabel: "Median Response Time in ms",
                axisLabelUseCanvas: true,
                axisLabelFontSizePixels: 12,
                axisLabelFontFamily: 'Verdana, Arial',
                axisLabelPadding: 20,
            },
            legend: {
                noColumns: 2,
                show: true,
                container: '#legendResponseTimeVsRequest'
            },
            selection: {
                mode: 'xy'
            },
            grid: {
                hoverable: true // IMPORTANT! this is needed for tooltip to work
            },
            tooltip: true,
            tooltipOpts: {
                content: "%s : Median response time at %x req/s was %y ms"
            },
            colors: ["#9ACD32", "#FF6347"]
        };
    },
    createGraph: function () {
        var data = this.data;
        var dataset = prepareData(data.result.series, $("#choicesResponseTimeVsRequest"));
        var options = this.getOptions();
        prepareOptions(options, data);
        $.plot($("#flotResponseTimeVsRequest"), dataset, options);
        // setup overview
        $.plot($("#overviewResponseTimeVsRequest"), dataset, prepareOverviewOptions(options));

    }
};

// Response Time vs Request
function refreshResponseTimeVsRequest() {
    var infos = responseTimeVsRequestInfos;
    prepareSeries(infos.data);
    if (isGraph($("#flotResponseTimeVsRequest"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesResponseTimeVsRequest");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotResponseTimeVsRequest", "#overviewResponseTimeVsRequest");
        $('#footerResponseRimeVsRequest .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};


var latenciesVsRequestInfos = {
    data: {"result": {"minY": 722.5, "minX": 11.0, "maxY": 3997.0, "series": [{"data": [[32.0, 1454.5], [33.0, 1401.0], [35.0, 1499.0], [11.0, 3436.0], [12.0, 3155.0], [13.0, 3702.0], [14.0, 722.5], [15.0, 3997.0], [17.0, 3297.0], [19.0, 2199.5], [20.0, 2702.0], [21.0, 2115.0], [22.0, 2152.5], [23.0, 2159.5], [24.0, 2001.0], [26.0, 1791.0], [27.0, 1611.0], [28.0, 1710.0], [29.0, 1584.5], [30.0, 1570.0], [31.0, 1663.5]], "isOverall": false, "label": "Successes", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 1000, "maxX": 35.0, "title": "Latencies Vs Request"}},
    getOptions: function() {
        return{
            series: {
                lines: {
                    show: false
                },
                points: {
                    show: true
                }
            },
            xaxis: {
                axisLabel: "Global number of requests per second",
                axisLabelUseCanvas: true,
                axisLabelFontSizePixels: 12,
                axisLabelFontFamily: 'Verdana, Arial',
                axisLabelPadding: 20,
            },
            yaxis: {
                axisLabel: "Median Latency in ms",
                axisLabelUseCanvas: true,
                axisLabelFontSizePixels: 12,
                axisLabelFontFamily: 'Verdana, Arial',
                axisLabelPadding: 20,
            },
            legend: { noColumns: 2,show: true, container: '#legendLatencyVsRequest' },
            selection: {
                mode: 'xy'
            },
            grid: {
                hoverable: true // IMPORTANT! this is needed for tooltip to work
            },
            tooltip: true,
            tooltipOpts: {
                content: "%s : Median Latency time at %x req/s was %y ms"
            },
            colors: ["#9ACD32", "#FF6347"]
        };
    },
    createGraph: function () {
        var data = this.data;
        var dataset = prepareData(data.result.series, $("#choicesLatencyVsRequest"));
        var options = this.getOptions();
        prepareOptions(options, data);
        $.plot($("#flotLatenciesVsRequest"), dataset, options);
        // setup overview
        $.plot($("#overviewLatenciesVsRequest"), dataset, prepareOverviewOptions(options));
    }
};

// Latencies vs Request
function refreshLatenciesVsRequest() {
        var infos = latenciesVsRequestInfos;
        prepareSeries(infos.data);
        if(isGraph($("#flotLatenciesVsRequest"))){
            infos.createGraph();
        }else{
            var choiceContainer = $("#choicesLatencyVsRequest");
            createLegend(choiceContainer, infos);
            infos.createGraph();
            setGraphZoomable("#flotLatenciesVsRequest", "#overviewLatenciesVsRequest");
            $('#footerLatenciesVsRequest .legendColorBox > div').each(function(i){
                $(this).clone().prependTo(choiceContainer.find("li").eq(i));
            });
        }
};

var hitsPerSecondInfos = {
        data: {"result": {"minY": 16.666666666666668, "minX": 1.77834756E12, "maxY": 16.666666666666668, "series": [{"data": [[1.77834756E12, 16.666666666666668]], "isOverall": false, "label": "hitsPerSecond", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.77834756E12, "title": "Hits Per Second"}},
        getOptions: function() {
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of hits / sec",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: "#legendHitsPerSecond"
                },
                selection: {
                    mode : 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s at %x was %y.2 hits/sec"
                }
            };
        },
        createGraph: function createGraph() {
            var data = this.data;
            var dataset = prepareData(data.result.series, $("#choicesHitsPerSecond"));
            var options = this.getOptions();
            prepareOptions(options, data);
            $.plot($("#flotHitsPerSecond"), dataset, options);
            // setup overview
            $.plot($("#overviewHitsPerSecond"), dataset, prepareOverviewOptions(options));
        }
};

// Hits per second
function refreshHitsPerSecond(fixTimestamps) {
    var infos = hitsPerSecondInfos;
    prepareSeries(infos.data);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 25200000);
    }
    if (isGraph($("#flotHitsPerSecond"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesHitsPerSecond");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotHitsPerSecond", "#overviewHitsPerSecond");
        $('#footerHitsPerSecond .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
}

var codesPerSecondInfos = {
        data: {"result": {"minY": 16.666666666666668, "minX": 1.77834756E12, "maxY": 16.666666666666668, "series": [{"data": [[1.77834756E12, 16.666666666666668]], "isOverall": false, "label": "200", "isController": false}], "supportsControllersDiscrimination": false, "granularity": 60000, "maxX": 1.77834756E12, "title": "Codes Per Second"}},
        getOptions: function(){
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of responses / sec",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: "#legendCodesPerSecond"
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "Number of Response Codes %s at %x was %y.2 responses / sec"
                }
            };
        },
    createGraph: function() {
        var data = this.data;
        var dataset = prepareData(data.result.series, $("#choicesCodesPerSecond"));
        var options = this.getOptions();
        prepareOptions(options, data);
        $.plot($("#flotCodesPerSecond"), dataset, options);
        // setup overview
        $.plot($("#overviewCodesPerSecond"), dataset, prepareOverviewOptions(options));
    }
};

// Codes per second
function refreshCodesPerSecond(fixTimestamps) {
    var infos = codesPerSecondInfos;
    prepareSeries(infos.data);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 25200000);
    }
    if(isGraph($("#flotCodesPerSecond"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesCodesPerSecond");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotCodesPerSecond", "#overviewCodesPerSecond");
        $('#footerCodesPerSecond .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var transactionsPerSecondInfos = {
        data: {"result": {"minY": 16.666666666666668, "minX": 1.77834756E12, "maxY": 16.666666666666668, "series": [{"data": [[1.77834756E12, 16.666666666666668]], "isOverall": false, "label": "GET_FilterCandidates-success", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.77834756E12, "title": "Transactions Per Second"}},
        getOptions: function(){
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of transactions / sec",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: "#legendTransactionsPerSecond"
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s at %x was %y transactions / sec"
                }
            };
        },
    createGraph: function () {
        var data = this.data;
        var dataset = prepareData(data.result.series, $("#choicesTransactionsPerSecond"));
        var options = this.getOptions();
        prepareOptions(options, data);
        $.plot($("#flotTransactionsPerSecond"), dataset, options);
        // setup overview
        $.plot($("#overviewTransactionsPerSecond"), dataset, prepareOverviewOptions(options));
    }
};

// Transactions per second
function refreshTransactionsPerSecond(fixTimestamps) {
    var infos = transactionsPerSecondInfos;
    prepareSeries(infos.data);
    if(infos.data.result.series.length == 0) {
        setEmptyGraph("#bodyTransactionsPerSecond");
        return;
    }
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 25200000);
    }
    if(isGraph($("#flotTransactionsPerSecond"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesTransactionsPerSecond");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotTransactionsPerSecond", "#overviewTransactionsPerSecond");
        $('#footerTransactionsPerSecond .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

var totalTPSInfos = {
        data: {"result": {"minY": 16.666666666666668, "minX": 1.77834756E12, "maxY": 16.666666666666668, "series": [{"data": [[1.77834756E12, 16.666666666666668]], "isOverall": false, "label": "Transaction-success", "isController": false}, {"data": [], "isOverall": false, "label": "Transaction-failure", "isController": false}], "supportsControllersDiscrimination": true, "granularity": 60000, "maxX": 1.77834756E12, "title": "Total Transactions Per Second"}},
        getOptions: function(){
            return {
                series: {
                    lines: {
                        show: true
                    },
                    points: {
                        show: true
                    }
                },
                xaxis: {
                    mode: "time",
                    timeformat: getTimeFormat(this.data.result.granularity),
                    axisLabel: getElapsedTimeLabel(this.data.result.granularity),
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20,
                },
                yaxis: {
                    axisLabel: "Number of transactions / sec",
                    axisLabelUseCanvas: true,
                    axisLabelFontSizePixels: 12,
                    axisLabelFontFamily: 'Verdana, Arial',
                    axisLabelPadding: 20
                },
                legend: {
                    noColumns: 2,
                    show: true,
                    container: "#legendTotalTPS"
                },
                selection: {
                    mode: 'xy'
                },
                grid: {
                    hoverable: true // IMPORTANT! this is needed for tooltip to
                                    // work
                },
                tooltip: true,
                tooltipOpts: {
                    content: "%s at %x was %y transactions / sec"
                },
                colors: ["#9ACD32", "#FF6347"]
            };
        },
    createGraph: function () {
        var data = this.data;
        var dataset = prepareData(data.result.series, $("#choicesTotalTPS"));
        var options = this.getOptions();
        prepareOptions(options, data);
        $.plot($("#flotTotalTPS"), dataset, options);
        // setup overview
        $.plot($("#overviewTotalTPS"), dataset, prepareOverviewOptions(options));
    }
};

// Total Transactions per second
function refreshTotalTPS(fixTimestamps) {
    var infos = totalTPSInfos;
    // We want to ignore seriesFilter
    prepareSeries(infos.data, false, true);
    if(fixTimestamps) {
        fixTimeStamps(infos.data.result.series, 25200000);
    }
    if(isGraph($("#flotTotalTPS"))){
        infos.createGraph();
    }else{
        var choiceContainer = $("#choicesTotalTPS");
        createLegend(choiceContainer, infos);
        infos.createGraph();
        setGraphZoomable("#flotTotalTPS", "#overviewTotalTPS");
        $('#footerTotalTPS .legendColorBox > div').each(function(i){
            $(this).clone().prependTo(choiceContainer.find("li").eq(i));
        });
    }
};

// Collapse the graph matching the specified DOM element depending the collapsed
// status
function collapse(elem, collapsed){
    if(collapsed){
        $(elem).parent().find(".fa-chevron-up").removeClass("fa-chevron-up").addClass("fa-chevron-down");
    } else {
        $(elem).parent().find(".fa-chevron-down").removeClass("fa-chevron-down").addClass("fa-chevron-up");
        if (elem.id == "bodyBytesThroughputOverTime") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshBytesThroughputOverTime(true);
            }
            document.location.href="#bytesThroughputOverTime";
        } else if (elem.id == "bodyLatenciesOverTime") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshLatenciesOverTime(true);
            }
            document.location.href="#latenciesOverTime";
        } else if (elem.id == "bodyCustomGraph") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshCustomGraph(true);
            }
            document.location.href="#responseCustomGraph";
        } else if (elem.id == "bodyConnectTimeOverTime") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshConnectTimeOverTime(true);
            }
            document.location.href="#connectTimeOverTime";
        } else if (elem.id == "bodyResponseTimePercentilesOverTime") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshResponseTimePercentilesOverTime(true);
            }
            document.location.href="#responseTimePercentilesOverTime";
        } else if (elem.id == "bodyResponseTimeDistribution") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshResponseTimeDistribution();
            }
            document.location.href="#responseTimeDistribution" ;
        } else if (elem.id == "bodySyntheticResponseTimeDistribution") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshSyntheticResponseTimeDistribution();
            }
            document.location.href="#syntheticResponseTimeDistribution" ;
        } else if (elem.id == "bodyActiveThreadsOverTime") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshActiveThreadsOverTime(true);
            }
            document.location.href="#activeThreadsOverTime";
        } else if (elem.id == "bodyTimeVsThreads") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshTimeVsThreads();
            }
            document.location.href="#timeVsThreads" ;
        } else if (elem.id == "bodyCodesPerSecond") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshCodesPerSecond(true);
            }
            document.location.href="#codesPerSecond";
        } else if (elem.id == "bodyTransactionsPerSecond") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshTransactionsPerSecond(true);
            }
            document.location.href="#transactionsPerSecond";
        } else if (elem.id == "bodyTotalTPS") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshTotalTPS(true);
            }
            document.location.href="#totalTPS";
        } else if (elem.id == "bodyResponseTimeVsRequest") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshResponseTimeVsRequest();
            }
            document.location.href="#responseTimeVsRequest";
        } else if (elem.id == "bodyLatenciesVsRequest") {
            if (isGraph($(elem).find('.flot-chart-content')) == false) {
                refreshLatenciesVsRequest();
            }
            document.location.href="#latencyVsRequest";
        }
    }
}

/*
 * Activates or deactivates all series of the specified graph (represented by id parameter)
 * depending on checked argument.
 */
function toggleAll(id, checked){
    var placeholder = document.getElementById(id);

    var cases = $(placeholder).find(':checkbox');
    cases.prop('checked', checked);
    $(cases).parent().children().children().toggleClass("legend-disabled", !checked);

    var choiceContainer;
    if ( id == "choicesBytesThroughputOverTime"){
        choiceContainer = $("#choicesBytesThroughputOverTime");
        refreshBytesThroughputOverTime(false);
    } else if(id == "choicesResponseTimesOverTime"){
        choiceContainer = $("#choicesResponseTimesOverTime");
        refreshResponseTimeOverTime(false);
    }else if(id == "choicesResponseCustomGraph"){
        choiceContainer = $("#choicesResponseCustomGraph");
        refreshCustomGraph(false);
    } else if ( id == "choicesLatenciesOverTime"){
        choiceContainer = $("#choicesLatenciesOverTime");
        refreshLatenciesOverTime(false);
    } else if ( id == "choicesConnectTimeOverTime"){
        choiceContainer = $("#choicesConnectTimeOverTime");
        refreshConnectTimeOverTime(false);
    } else if ( id == "choicesResponseTimePercentilesOverTime"){
        choiceContainer = $("#choicesResponseTimePercentilesOverTime");
        refreshResponseTimePercentilesOverTime(false);
    } else if ( id == "choicesResponseTimePercentiles"){
        choiceContainer = $("#choicesResponseTimePercentiles");
        refreshResponseTimePercentiles();
    } else if(id == "choicesActiveThreadsOverTime"){
        choiceContainer = $("#choicesActiveThreadsOverTime");
        refreshActiveThreadsOverTime(false);
    } else if ( id == "choicesTimeVsThreads"){
        choiceContainer = $("#choicesTimeVsThreads");
        refreshTimeVsThreads();
    } else if ( id == "choicesSyntheticResponseTimeDistribution"){
        choiceContainer = $("#choicesSyntheticResponseTimeDistribution");
        refreshSyntheticResponseTimeDistribution();
    } else if ( id == "choicesResponseTimeDistribution"){
        choiceContainer = $("#choicesResponseTimeDistribution");
        refreshResponseTimeDistribution();
    } else if ( id == "choicesHitsPerSecond"){
        choiceContainer = $("#choicesHitsPerSecond");
        refreshHitsPerSecond(false);
    } else if(id == "choicesCodesPerSecond"){
        choiceContainer = $("#choicesCodesPerSecond");
        refreshCodesPerSecond(false);
    } else if ( id == "choicesTransactionsPerSecond"){
        choiceContainer = $("#choicesTransactionsPerSecond");
        refreshTransactionsPerSecond(false);
    } else if ( id == "choicesTotalTPS"){
        choiceContainer = $("#choicesTotalTPS");
        refreshTotalTPS(false);
    } else if ( id == "choicesResponseTimeVsRequest"){
        choiceContainer = $("#choicesResponseTimeVsRequest");
        refreshResponseTimeVsRequest();
    } else if ( id == "choicesLatencyVsRequest"){
        choiceContainer = $("#choicesLatencyVsRequest");
        refreshLatenciesVsRequest();
    }
    var color = checked ? "black" : "#818181";
    if(choiceContainer != null) {
        choiceContainer.find("label").each(function(){
            this.style.color = color;
        });
    }
}

