package com.deploytrack.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.deploytrack.IntegrationTestBase;
import com.deploytrack.dto.CreateDeploymentRequest;
import com.deploytrack.dto.CreateProjectRequest;
import com.deploytrack.entity.Environment;
import com.deploytrack.entity.Project;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

// Guards against the N+1 regression. Asserting on behaviour ("the page is
// correct") would pass either way -- N+1 returns the right data, just with
// one query per row. The only way to catch it is to count the SQL actually
// issued, which is what Hibernate's Statistics gives us.
@TestPropertySource(properties = {
    "deploytrack.simulator.enabled=false",
    "spring.jpa.properties.hibernate.generate_statistics=true"
})
class ProjectListQueryCountIT extends IntegrationTestBase {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private SessionFactory sessionFactory;

    @Test
    @Transactional
    @WithMockUser(username = "admin@test.local", roles = "ADMIN")
    void resolvingLatestDeploymentsCostsOneQueryRegardlessOfProjectCount() {
        int projectCount = 10;
        for (int i = 0; i < projectCount; i++) {
            Project project = projectService.create(
                new CreateProjectRequest("n1-probe-" + UUID.randomUUID(), "query count fixture"));
            deploymentService.trigger(project.getId(),
                new CreateDeploymentRequest("1.0." + i, Environment.PRODUCTION));
        }

        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        var page = projectService.list("n1-probe-", PageRequest.of(0, projectCount));
        var latest = projectService.latestDeploymentsFor(page.getContent());

        assertThat(page.getContent()).hasSize(projectCount);
        assertThat(latest).hasSize(projectCount);

        // One query for the page of projects, one count query for pagination,
        // and one batched query for every latest deployment. The N+1 version
        // of this would issue 10 extra queries here and grow with the page.
        assertThat(statistics.getPrepareStatementCount())
            .as("listing %d projects with their latest deployment should not scale with project count",
                projectCount)
            .isLessThanOrEqualTo(4);
    }
}
