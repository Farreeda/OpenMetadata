/*
 *  Copyright 2024 Collate.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import {
  expect,
  Page,
  PlaywrightTestArgs,
  PlaywrightWorkerArgs,
  TestType,
} from '@playwright/test';
import { DBT, HTTP_CONFIG_SOURCE, REDSHIFT } from '../../../constant/service';
import { SidebarItem } from '../../../constant/sidebar';
import {
  getApiContext,
  redirectToHomePage,
  toastNotification,
} from '../../../utils/common';
import { visitEntityPage } from '../../../utils/entity';
import { visitServiceDetailsPage } from '../../../utils/service';
import {
  checkServiceFieldSectionHighlighting,
  Services,
} from '../../../utils/serviceIngestion';
import { sidebarClick } from '../../../utils/sidebar';
import ServiceBaseClass from './ServiceBaseClass';

class RedshiftWithDBTIngestionClass extends ServiceBaseClass {
  name: string;
  filterPattern: string;
  dbtEntityFqn: string;
  schemaFilterPattern = 'dbt_automate_upgrade_tests';

  constructor() {
    super(
      Services.Database,
      REDSHIFT.serviceName,
      REDSHIFT.serviceType,
      REDSHIFT.tableName
    );

    const redshiftDatabase = process.env.PLAYWRIGHT_REDSHIFT_DATABASE ?? '';

    this.filterPattern = 'sales';
    this.dbtEntityFqn = `${REDSHIFT.serviceName}.${redshiftDatabase}.${this.schemaFilterPattern}.${REDSHIFT.DBTTable}`;
  }

  async createService(page: Page) {
    await super.createService(page);
  }

  async updateService(page: Page) {
    await super.updateService(page);
  }

  async fillConnectionDetails(page: Page) {
    const redshiftUsername = process.env.PLAYWRIGHT_REDSHIFT_USERNAME ?? '';
    const redshiftPassword = process.env.PLAYWRIGHT_REDSHIFT_PASSWORD ?? '';
    const redshiftHost = process.env.PLAYWRIGHT_REDSHIFT_HOST ?? '';
    const redshiftDatabase = process.env.PLAYWRIGHT_REDSHIFT_DATABASE ?? '';

    await page.fill('#root\\/username', redshiftUsername);
    await checkServiceFieldSectionHighlighting(page, 'username');
    await page.fill('#root\\/password', redshiftPassword);
    await checkServiceFieldSectionHighlighting(page, 'password');
    await page.fill('#root\\/hostPort', redshiftHost);
    await checkServiceFieldSectionHighlighting(page, 'hostPort');
    await page.fill('#root\\/database', redshiftDatabase);
    await checkServiceFieldSectionHighlighting(page, 'database');
  }

  async fillIngestionDetails(page: Page) {
    // no schema or database filters
    await page
      .locator('#root\\/schemaFilterPattern\\/includes')
      .fill(this.schemaFilterPattern);

    await page.locator('#root\\/schemaFilterPattern\\/includes').press('Enter');
  }

  async runAdditionalTests(
    page: Page,
    test: TestType<PlaywrightTestArgs, PlaywrightWorkerArgs>
  ) {
    await test.step('Add DBT ingestion', async () => {
      const { apiContext } = await getApiContext(page);
      await redirectToHomePage(page);
      await visitServiceDetailsPage(
        page,
        {
          type: this.category,
          name: this.serviceName,
          displayName: this.serviceName,
        },
        true
      );

      await page.click('[data-testid="ingestions"]');
      await page.waitForSelector('[data-testid="ingestion-details-container"]');
      await page.waitForTimeout(1000);
      await page.click('[data-testid="add-new-ingestion-button"]');
      await page.waitForTimeout(1000);
      await page.click('[data-menu-id*="dbt"]');

      await page.waitForSelector('#root\\/dbtConfigSource__oneof_select');
      await page.selectOption(
        '#root\\/dbtConfigSource__oneof_select',
        'DBT HTTP Config'
      );
      await page.fill(
        '#root\\/dbtConfigSource\\/dbtCatalogHttpPath',
        HTTP_CONFIG_SOURCE.DBT_CATALOG_HTTP_PATH
      );
      await page.fill(
        '#root\\/dbtConfigSource\\/dbtManifestHttpPath',
        HTTP_CONFIG_SOURCE.DBT_MANIFEST_HTTP_PATH
      );
      await page.fill(
        '#root\\/dbtConfigSource\\/dbtRunResultsHttpPath',
        HTTP_CONFIG_SOURCE.DBT_RUN_RESULTS_FILE_PATH
      );

      await page.click('[data-testid="submit-btn"]');
      // Make sure we create ingestion with None schedule to avoid conflict between Airflow and Argo behavior
      await this.scheduleIngestion(page);

      await page.click('[data-testid="view-service-button"]');

      // Header available once page loads
      await page.waitForSelector('[data-testid="data-assets-header"]');
      await page.getByTestId('loader').waitFor({ state: 'detached' });
      await page.getByTestId('ingestions').click();
      await page
        .getByLabel('Ingestions')
        .getByTestId('loader')
        .waitFor({ state: 'detached' });

      const response = await apiContext
        .get(
          `/api/v1/services/ingestionPipelines?service=${encodeURIComponent(
            this.serviceName
          )}&pipelineType=dbt&serviceType=databaseService&limit=1`
        )
        .then((res) => res.json());

      await page.click(
        `[data-row-key*="${response.data[0].name}"] [data-testid="more-actions"]`
      );
      await page.getByTestId('run-button').click();

      await toastNotification(page, `Pipeline triggered successfully!`);

      await this.handleIngestionRetry('dbt', page);
    });

    await test.step('Validate DBT is ingested properly', async () => {
      await sidebarClick(page, SidebarItem.TAGS);

      await page.waitForSelector('[data-testid="data-summary-container"]');
      await page.click(
        `[data-testid="data-summary-container"] >> text=${DBT.classification}`
      );

      // Verify DBT tag category is added
      await page.waitForSelector('[data-testid="tag-name"]');
      const tagName = await page.textContent('[data-testid="tag-name"]');

      expect(tagName).toContain(DBT.classification);

      await page.waitForSelector('.ant-table-row');

      await expect(page.getByRole('cell', { name: DBT.tagName })).toBeVisible();

      // Verify DBT in table entity
      await visitEntityPage({
        page,
        searchTerm: REDSHIFT.DBTTable,
        dataTestId: `${REDSHIFT.serviceName}-${REDSHIFT.DBTTable}`,
      });

      // Verify tags
      await page.waitForSelector('[data-testid="entity-tags"]');

      await expect(
        page
          .getByTestId('entity-right-panel')
          .getByTestId('tags-container')
          .getByTestId('entity-tags')
      ).toContainText(DBT.tagName);

      // Verify DBT tab is present
      await page.click('[data-testid="dbt"]');

      // Verify query is present in the DBT tab
      await page.waitForSelector('.CodeMirror');
      const codeMirrorText = await page.textContent('.CodeMirror');

      expect(codeMirrorText).toContain(DBT.dbtQuery);

      await page.click('[data-testid="lineage"]');

      await page.waitForSelector('[data-testid="entity-header-display-name"]');
      const entityHeaderDisplayName = await page.textContent(
        '[data-testid="entity-header-display-name"]'
      );

      expect(entityHeaderDisplayName).toContain(DBT.dbtLineageNodeLabel);

      // Verify Data Quality
      await page.click('[data-testid="profiler"]');

      await page.waitForSelector('[data-testid="profiler-tab-left-panel"]');
      await page.getByRole('menuitem', { name: 'Data Quality' }).click();

      await expect(page.getByTestId(DBT.dataQualityTest1)).toHaveText(
        DBT.dataQualityTest1
      );

      await expect(page.getByTestId(DBT.dataQualityTest1)).toHaveText(
        DBT.dataQualityTest1
      );
    });
  }

  async deleteService(page: Page) {
    await super.deleteService(page);
  }
}

// eslint-disable-next-line jest/no-export
export default RedshiftWithDBTIngestionClass;