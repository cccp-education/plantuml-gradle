"""SmolLM2-135M-Instruct LoRA fine-tuning — smoke test pipeline.

Usage:
    python fine_tune.py [--model-dir /path/to/model]

Environment:
    MODEL_DIR — path to pre-downloaded HuggingFace model (default: /model)
    OUTPUT_DIR — where to save LoRA adapter (default: /tmp/lora-adapter)
    MAX_STEPS  — max training steps (default: 5, set to 1 for CI smoke test)
    HF_TOKEN   — optional HuggingFace token for gated models (not needed for SmolLM2)

Returns exit code 0 on success, non-zero on failure.
"""
import os
import sys
import json
import time
import argparse
import logging

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
    stream=sys.stdout,
)
log = logging.getLogger("fine_tune")


def main():
    parser = argparse.ArgumentParser(description="SmolLM2 LoRA fine-tuning smoke test")
    parser.add_argument("--model-dir", default=os.environ.get("MODEL_DIR", "/model"))
    parser.add_argument("--output-dir", default=os.environ.get("OUTPUT_DIR", "/tmp/lora-adapter"))
    parser.add_argument("--max-steps", type=int, default=int(os.environ.get("MAX_STEPS", "5")))
    args = parser.parse_args()

    model_dir = args.model_dir
    output_dir = args.output_dir
    max_steps = args.max_steps

    if not os.path.isdir(model_dir) or not os.path.isfile(os.path.join(model_dir, "model.safetensors")):
        log.error("model.safetensors not found in %s", model_dir)
        listed = os.listdir(model_dir) if os.path.isdir(model_dir) else ["<directory missing>"]
        log.error("Directory contents: %s", listed)
        sys.exit(1)

    log.info("model_dir=%s output_dir=%s max_steps=%d", model_dir, output_dir, max_steps)

    t0 = time.time()

    import torch
    from transformers import AutoTokenizer, AutoModelForCausalLM
    from peft import LoraConfig, get_peft_model
    from datasets import Dataset
    from trl import SFTConfig, SFTTrainer

    device = "cuda" if torch.cuda.is_available() else "cpu"
    log.info("PyTorch %s — CUDA: %s — device: %s", torch.__version__, torch.cuda.is_available(), device)

    log.info("Loading tokenizer from %s ...", model_dir)
    tokenizer = AutoTokenizer.from_pretrained(model_dir, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    log.info("Loading model from %s ...", model_dir)
    model = AutoModelForCausalLM.from_pretrained(
        model_dir,
        dtype=torch.float32 if device == "cpu" else torch.float16,
        trust_remote_code=True,
    ).to(device)
    model.config.use_cache = False

    lora_config = LoraConfig(
        r=8,
        lora_alpha=16,
        target_modules=["q_proj", "v_proj", "k_proj", "o_proj"],
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM",
    )
    model = get_peft_model(model, lora_config)
    trainable, total = model.get_nb_trainable_parameters()
    log.info("Trainable params: %s / %s (%.2f%%)", f"{trainable:,}", f"{total:,}", 100 * trainable / total)

    synthetic_data = [
        {"instruction": "What is the capital of France?", "output": "Paris."},
        {"instruction": "What is 2 + 2?", "output": "4."},
        {"instruction": "Translate 'hello' to Spanish.", "output": "Hola."},
        {"instruction": "What is H2O?", "output": "Water."},
    ]

    def format_example(example):
        text = (
            f"<|im_start|>user\n{example['instruction']}<|im_end|>\n"
            f"<|im_start|>assistant\n{example['output']}<|im_end|>"
        )
        return {"text": text}

    dataset = Dataset.from_list(synthetic_data).map(format_example)
    log.info("Dataset: %d examples", len(dataset))

    training_args = SFTConfig(
        output_dir=output_dir,
        num_train_epochs=1,
        per_device_train_batch_size=1,
        gradient_accumulation_steps=1,
        learning_rate=2e-4,
        max_steps=max_steps,
        logging_steps=1,
        save_steps=max_steps,
        save_total_limit=1,
        fp16=False,
        bf16=False,
        report_to="none",
        remove_unused_columns=False,
        max_length=128,
    )

    trainer = SFTTrainer(
        model=model,
        args=training_args,
        train_dataset=dataset,
        processing_class=tokenizer,
    )

    log.info("Starting training (%d steps) ...", max_steps)
    train_result = trainer.train()

    adapter_dir = os.path.join(output_dir, "adapter")
    os.makedirs(adapter_dir, exist_ok=True)
    trainer.model.save_pretrained(adapter_dir)
    tokenizer.save_pretrained(adapter_dir)
    log.info("Adapter saved to %s", adapter_dir)

    loss = train_result.training_loss if train_result.training_loss else 0.0
    elapsed = time.time() - t0
    log.info("Training loss: %.4f — elapsed: %.1fs", loss, elapsed)

    metrics = {
        "status": "ok",
        "trainable_params": trainable,
        "total_params": total,
        "training_loss": loss,
        "elapsed_seconds": elapsed,
        "max_steps": max_steps,
        "device": device,
        "model": "HuggingFaceTB/SmolLM2-135M-Instruct",
    }
    with open(os.path.join(output_dir, "metrics.json"), "w") as f:
        json.dump(metrics, f, indent=2)

    log.info("SUCCESS — pipeline validated")
    sys.exit(0)


if __name__ == "__main__":
    main()
