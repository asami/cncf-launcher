# Service Bus Migration

Date: 2026-09-24

Decision: do not replace cncf-launcher's dedicated management area when Service Bus support is introduced. Add lifecycle event publication in parallel, validate the Service Bus Journal in real operation, then migrate historical responsibilities only after it is stable. Current state remains registry/storage oriented; operational history moves toward the journal.
