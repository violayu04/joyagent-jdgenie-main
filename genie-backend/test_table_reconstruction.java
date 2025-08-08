import java.util.*;

public class test_table_reconstruction {
    public static void main(String[] args) {
        // Test the table reconstruction logic with sample table lines
        List<String> testTableLines = Arrays.asList(
            "| 指标 | 2023年 | 2022年 | 变化 |",
            "|------|--------|--------|------|",
            "| 年化归属于母公司普通股股东 | 11.08% | 11.88% | 下降 |",
            "| 的加权平均净资产收益率 | | | 0.80个百分点 |",
            "| 营业收入 | 1,234,567万元 | 1,123,456万元 | 增长 |",
            "| | | | 9.9% |",
            "| 净利润 | 234,567万元 | 212,345万元 | 增长 |",
            "| (归属于母公司股东) | | | 10.5% |"
        );
        
        System.out.println("Original table lines:");
        for (String line : testTableLines) {
            System.out.println(line);
        }
        
        System.out.println("\nThis demonstrates the multi-line cell issue:");
        System.out.println("- '年化归属于母公司普通股股东' and '的加权平均净资产收益率' should be one cell");
        System.out.println("- '下降' and '0.80个百分点' should be one cell");
        System.out.println("- Similar pattern for other rows");
        
        System.out.println("\nThe enhanced SemanticTextChunkingService should now:");
        System.out.println("1. Detect these as table lines");
        System.out.println("2. Analyze table structure to identify logical rows vs physical rows");
        System.out.println("3. Reconstruct cells by combining fragments from multiple physical rows");
        System.out.println("4. Output a properly formatted table with complete cell contents");
    }
}