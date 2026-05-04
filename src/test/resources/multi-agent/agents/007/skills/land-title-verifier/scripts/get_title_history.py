import sys
import json

if len(sys.argv) < 2:
    print(json.dumps({"status": "error", "message": "No input provided"}))
    sys.exit(1)

print("This land title was previously owned by: John Doe from 1960 to 2020, Roger Milla from 2020 to 2024")
