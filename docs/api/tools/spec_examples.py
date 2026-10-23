"""Read the JSON examples of docs/api/api-spec.md, shared by gen_openapi.py and extract_fixtures.py.

The examples are the ```json blocks of the spec, in document order. Each one gets a stable snake_case name, which
is also the fixture file name in :core:testing (technical-design §14). Adding, removing or reordering a block in
api-spec.md means updating NAMES below. Standard library only.
"""
import json
import re

# One name per ```json block of api-spec.md, in document order.
NAMES = (
    "error",
    "e1_request",
    "e1_response",
    "e1_error_not_registered",
    "e2_response",
    "e3_request",
    "e3_response",
    "e5_response",
    "e6_response",
    "e7_request",
    "e7_request_combo_line",
    "e7_response",
    "e7_error_price_changed",
    "e7_error_item_unavailable",
    "e8_request",
    "e8_response",
    "e9_request_qris",
    "e9_response_qris",
    "e9_request_card",
    "e9_response_card",
    "e10_response_pending",
    "e10_response_paid",
    "e10_response_failed",
    "e11_request",
    "e11_response",
    "e11_error_not_cancellable",
    "e12_request",
    "e12_response",
    "e13_request",
    "e13_response",
    "e14_response",
)

_BLOCK = re.compile(r"```json\n(.*?)```", re.S)


def read_examples(spec_path):
    """Returns the spec's JSON examples as an ordered list of (name, raw_text, parsed).

    raw_text is the block exactly as written in the spec, including its final newline.
    """
    with open(spec_path, encoding="utf-8") as f:
        raw_blocks = _BLOCK.findall(f.read())
    if len(raw_blocks) != len(NAMES):
        raise ValueError(
            f"{spec_path}: expected {len(NAMES)} ```json blocks, found {len(raw_blocks)}; "
            "update NAMES in docs/api/tools/spec_examples.py"
        )
    examples = []
    for name, raw in zip(NAMES, raw_blocks):
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError as e:
            raise ValueError(f"{spec_path}: example '{name}' is not valid JSON: {e}") from e
        examples.append((name, raw, parsed))
    return examples
