package br.com.worm.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.PrintWriter;
import java.io.StringWriter;

@RestController
@RequestMapping("/benchmark")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @GetMapping(produces = "text/csv")
    public String runBenchmark(
            @RequestParam(defaultValue = "10") int fast,
            @RequestParam(defaultValue = "100") int medium,
            @RequestParam(defaultValue = "1000") int heavy) {
            
        int[] workloads = {fast, medium, heavy}; // Rapido, Medio, Intenso
        String[] sizes = {"RAPIDO", "MEDIO", "INTENSO"};
        
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        
        pw.println("Operacao,Carga,Tamanho,ORM,Tempo(ms)");
        
        for (int i = 0; i < workloads.length; i++) {
            int count = workloads[i];
            String size = sizes[i];

            // Unitary Insert
            pw.printf("Insert Unitario,%d,%s,WORM,%d%n", count, size, benchmarkService.wormUnitaryInsert(count));
            pw.printf("Insert Unitario,%d,%s,JPA,%d%n", count, size, benchmarkService.jpaUnitaryInsert(count));

            // Batch Insert
            pw.printf("Insert Batch,%d,%s,WORM,%d%n", count, size, benchmarkService.wormBatchInsert(count));
            pw.printf("Insert Batch,%d,%s,JPA,%d%n", count, size, benchmarkService.jpaBatchInsert(count));

            // Unitary Update
            pw.printf("Update Unitario,%d,%s,WORM,%d%n", count, size, benchmarkService.wormUnitaryUpdate(count));
            pw.printf("Update Unitario,%d,%s,JPA,%d%n", count, size, benchmarkService.jpaUnitaryUpdate(count));

            // Batch Update
            pw.printf("Update Batch,%d,%s,WORM,%d%n", count, size, benchmarkService.wormBatchUpdate(count));
            pw.printf("Update Batch,%d,%s,JPA,%d%n", count, size, benchmarkService.jpaBatchUpdate(count));

            // Select by ID
            pw.printf("Select By ID,%d,%s,WORM,%d%n", count, size, benchmarkService.wormSelectById(count));
            pw.printf("Select By ID,%d,%s,JPA,%d%n", count, size, benchmarkService.jpaSelectById(count));

            // Select All
            pw.printf("Select All,%d,%s,WORM,%d%n", count, size, benchmarkService.wormSelectAll(count));
            pw.printf("Select All,%d,%s,JPA,%d%n", count, size, benchmarkService.jpaSelectAll(count));

            // Select Filtered
            pw.printf("Select Com Filtro,%d,%s,WORM,%d%n", count, size, benchmarkService.wormSelectFiltered(count));
            pw.printf("Select Com Filtro,%d,%s,JPA,%d%n", count, size, benchmarkService.jpaSelectFiltered(count));

            // Soft Delete Unitary
            pw.printf("Soft Delete Unitario,%d,%s,WORM,%d%n", count, size, benchmarkService.wormSoftDeleteUnitary(count));
            pw.printf("Soft Delete Unitario,%d,%s,JPA,%d%n", count, size, benchmarkService.jpaSoftDeleteUnitary(count));
            
            // Soft Delete Batch
            pw.printf("Soft Delete Batch,%d,%s,WORM,%d%n", count, size, benchmarkService.wormSoftDeleteBatch(count));
            pw.printf("Soft Delete Batch,%d,%s,JPA,%d%n", count, size, benchmarkService.jpaSoftDeleteBatch(count));

            // Hard Delete Unitary
            pw.printf("Hard Delete Unitario,%d,%s,WORM,%d%n", count, size, benchmarkService.wormHardDeleteUnitary(count));
            pw.printf("Hard Delete Unitario,%d,%s,JPA,%d%n", count, size, benchmarkService.jpaHardDeleteUnitary(count));
            
            // Hard Delete Batch
            pw.printf("Hard Delete Batch,%d,%s,WORM,%d%n", count, size, benchmarkService.wormHardDeleteBatch(count));
            pw.printf("Hard Delete Batch,%d,%s,JPA,%d%n", count, size, benchmarkService.jpaHardDeleteBatch(count));
        }

        // Clean up
        benchmarkService.cleanDatabase();
        benchmarkService.cleanDatabaseJpa();

        return sw.toString();
    }
}
