-- Phase 4: image embeddings for visual similarity
-- One embedding row per (media, model) to keep the pipeline idempotent.
ALTER TABLE image_embeddings
    ADD UNIQUE KEY uk_image_embeddings_media_model (media_id, model_name);
