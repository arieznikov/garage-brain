# Car Scanner export fixtures

Real Car Scanner exports used for parser regression tests. **GPS latitude/longitude are replaced with synthetic coordinates** (`45.0000000`, `-93.0000000`) so public repos do not expose driving routes.

## Layout

| Directory | Format | Notes |
|-----------|--------|-------|
| `exported_records_horizontal/` | Wide CSV, comma-separated | Sparse rows; wall-clock `time` column |
| `exported_records_horizontal_backfill/` | Wide CSV, comma-separated | Same sessions as horizontal, with prior values backfilled |
| `exported_records_vertical/` | Long CSV, semicolon-separated | `SECONDS`, `PID`, `VALUE`, `UNITS`, GPS columns |

Synthetic CSV #2 samples (`car-scanner-csv2-*.csv`) remain for the minimal tutorial format.

## Export paths in Car Scanner

- **CSV #2:** Data recording → Share → CSV #2
- **Horizontal / vertical:** Data recording → Share → pick layout in export options

## Adding fixtures

1. Export from Car Scanner (prefer the same vehicle/PIDs you care about).
2. Drop files into the appropriate folder.
3. Run from repo root:

   ```bash
   python3 scripts/redact-fixture-gps.py
   ```

4. Run `./mvnw verify` in `backend/` to confirm parser and golden-drift tests still pass.

Strip VINs, license plates, and custom profile names from filenames or headers if they could identify you.
