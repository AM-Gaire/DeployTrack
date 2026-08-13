-- Deleting a project failed with a 500 whenever it had any deployments.
--
-- V1 declared these foreign keys without saying what should happen to child
-- rows when a parent is removed, so Postgres defaulted to NO ACTION and
-- refused the delete. A project with no deployments deleted fine, which is
-- why the bug only appeared on projects that had actually been used.
--
-- Cascade is the intended behaviour, not a workaround: the confirmation
-- dialog tells the user deleting a project "removes its deployment history
-- and logs", and a deployment cannot meaningfully exist without its project.
--
-- Done in the database rather than with JPA cascade or orphanRemoval, which
-- would load every deployment and every log line into memory to delete them
-- one at a time. Logs are the fastest-growing table here, so that approach
-- gets slower exactly as it matters more.

ALTER TABLE deployments DROP CONSTRAINT fk_deployments_project;
ALTER TABLE deployments
    ADD CONSTRAINT fk_deployments_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;

ALTER TABLE logs DROP CONSTRAINT fk_logs_deployment;
ALTER TABLE logs
    ADD CONSTRAINT fk_logs_deployment
    FOREIGN KEY (deployment_id) REFERENCES deployments (id) ON DELETE CASCADE;

-- The user references are deliberately left as NO ACTION. Deleting a person
-- should never silently delete the projects they created or rewrite who
-- deployed what -- the deployment history is a record of what happened, and
-- it stays accurate even after someone leaves.
