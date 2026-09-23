# Platform Subcomponent Installation

cncf-launcher will resolve platform-specific Subcomponents from CAR metadata, select the artifact matching the current OS/architecture, and expose extraction/installation handoff facilities.

It must support both physical forms: an artifact bundled in the parent CAR and a Subcomponent delivered as a separate CAR. These are distribution choices, not different logical Subcomponents.

The first reference case is the textus-control-center macOS Menu Bar application.
