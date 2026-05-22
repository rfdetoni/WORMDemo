# Benchmark comparativo: Native SQL vs WORM vs WORM-dsl

Este projeto inclui um benchmark JUnit para comparar os 3 caminhos sobre o **mesmo dataset**:

- `NATIVE_SQL`: `JdbcTemplate` com SQL explícito
- `WORM`: `FilterBuilder` com colunas por string
- `WORM_DSL`: `FilterBuilder` tipado com metamodelo gerado (`BookProjection_`)

O teste responsável é:

`WormDslStackComparisonBenchmarkTest#runAndExportNativeWormAndWormDslComparison`

## Como rodar

```bash
mvn -Dtest=WormDslStackComparisonBenchmarkTest#runAndExportNativeWormAndWormDslComparison test
```

### Parâmetros

- `benchmark.compare.rounds` (default `3`)
- `benchmark.compare.ops.scale` (default `1`)

Exemplo rápido:

```bash
mvn -Dtest=WormDslStackComparisonBenchmarkTest#runAndExportNativeWormAndWormDslComparison \
    -Dbenchmark.compare.rounds=1 \
    -Dbenchmark.compare.ops.scale=1 test
```

## Saída

O benchmark gera CSV na raiz do projeto:

`WormDslStackComparisonBenchmark_<yyyyMMdd_HHmmss>.csv`

Colunas:

- `scenario`
- `stack`
- `avg_us`
- `p95_us`
- `throughput_ops_per_sec`
- `effective_ops_per_round`
- `rows`
- `checksum`

## Metamodelo gerado automaticamente no compile

Com `worm-processor` habilitado no `maven-compiler-plugin`, o compile gera classes tipadas em:

`target/generated-sources/annotations`

Incluindo metamodelo para `record` de projeção, por exemplo:

- `br.com.worm.demo.dto.BookProjection_`
