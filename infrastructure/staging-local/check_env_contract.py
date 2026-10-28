"""Comprueba que el staging local pasa a la app las mismas variables de entorno que App Runner.

El smoke solo demuestra que la app arranca con lo que le da el docker-compose; si ese contrato se separa del de
Terraform, el staging local seguiría en verde y el primer despliegue en AWS fallaría igual. Compara las claves del
servicio `app` del compose con la unión de `base_env` y `runtime_secrets` del módulo `runtime` y de
`extra_environment_variables` y `extra_secrets` de `environments/staging`.

Uso (desde la raíz del repo): python3 infrastructure/staging-local/check_env_contract.py
"""

import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMPOSE = ROOT / "infrastructure/staging-local/docker-compose.yml"
TERRAFORM_BLOCKS = {
    ROOT / "infrastructure/terraform/modules/runtime/main.tf": ["base_env", "runtime_secrets"],
    ROOT / "infrastructure/terraform/environments/staging/main.tf": ["extra_environment_variables", "extra_secrets"],
}
KEY = re.compile(r"^\s*([A-Z][A-Z0-9_]*)\s*=")


def block_keys(path: Path, name: str) -> set[str]:
    """Claves de un mapa HCL `name = { ... }`, cerrado por la llave con la misma sangría que la apertura."""
    lines = path.read_text(encoding="utf-8").splitlines()
    opening = re.compile(rf"^(\s*){re.escape(name)}\s*=\s*\{{\s*$")
    for index, line in enumerate(lines):
        match = opening.match(line)
        if not match:
            continue
        closing = f"{match.group(1)}}}"
        keys = set()
        for inner in lines[index + 1:]:
            if inner.rstrip() == closing:
                return keys
            key = KEY.match(inner)
            if key:
                keys.add(key.group(1))
        sys.exit(f"{path}: el bloque {name} no se cierra")
    sys.exit(f"{path}: no se encuentra el bloque {name}")


def compose_keys() -> set[str]:
    config = subprocess.run(
        ["docker", "compose", "-f", str(COMPOSE), "config", "--format", "json"],
        check=True,
        capture_output=True,
        text=True,
    )
    return set(json.loads(config.stdout)["services"]["app"]["environment"])


def main() -> None:
    terraform = set().union(*(block_keys(path, name) for path, names in TERRAFORM_BLOCKS.items() for name in names))
    compose = compose_keys()
    if terraform == compose:
        print(f"ok  el compose pasa las mismas {len(compose)} variables que App Runner")
        return
    if terraform - compose:
        print(f"Faltan en el compose (las pasa App Runner): {sorted(terraform - compose)}", file=sys.stderr)
    if compose - terraform:
        print(f"Sobran en el compose (App Runner no las pasa): {sorted(compose - terraform)}", file=sys.stderr)
    sys.exit(1)


if __name__ == "__main__":
    main()
