import { expect, test, type Page } from '@playwright/test';

const vcRepoUrl = 'https://credentials.test';
const issuerUrl = 'https://issuer.test';
const walletUrl = 'https://wallet.test';
const credentialId = 'OpenBadgeCredential';
const credentialOfferUrl =
  'openid-credential-offer://?credential_offer_uri=https://issuer.test/openid4vci/credential-offer?id=offer-123';

const credential = {
  '@context': ['https://www.w3.org/2018/credentials/v1'],
  type: ['VerifiableCredential', credentialId],
  issuer: {
    id: '',
    name: 'Jobs For The Future (JFF)',
  },
  credentialSubject: {
    id: 'did:example:holder',
    name: 'Jane Doe',
  },
};

const mapping = {
  id: '<uuid>',
  issuer: {
    id: '<issuerDid>',
  },
  credentialSubject: {
    id: '<subjectDid>',
  },
  issuanceDate: '<timestamp>',
};

async function mockOfferDependencies(page: Page) {
  await page.route('**/api/env', (route) =>
    route.fulfill({
      json: {
        NEXT_PUBLIC_VC_REPO: vcRepoUrl,
        NEXT_PUBLIC_ISSUER: issuerUrl,
        NEXT_PUBLIC_WALLET: walletUrl,
      },
    })
  );

  await page.route(`${vcRepoUrl}/api/list`, (route) =>
    route.fulfill({
      json: [credentialId],
    })
  );

  await page.route(`${vcRepoUrl}/api/vc/${credentialId}`, (route) =>
    route.fulfill({
      json: credential,
    })
  );

  await page.route(`${vcRepoUrl}/api/mapping/${credentialId}`, (route) =>
    route.fulfill({
      json: mapping,
    })
  );

  await page.route(
    `${issuerUrl}/draft13/.well-known/openid-credential-issuer`,
    (route) =>
      route.fulfill({
        json: {
          credential_configurations_supported: {
            [`${credentialId}_jwt_vc_json`]: {
              format: 'jwt_vc_json',
            },
          },
        },
      })
  );
}

test('creates an offer for the selected credential configuration', async ({
  page,
}) => {
  let issueRequestBody: Record<string, unknown> | undefined;

  await mockOfferDependencies(page);
  await page.route(`${issuerUrl}/openid4vc/jwt/issue`, async (route) => {
    issueRequestBody = route.request().postDataJSON();
    await route.fulfill({
      json: credentialOfferUrl,
    });
  });

  const credentialLoaded = page.waitForResponse(
    (response) =>
      response.url() === `${vcRepoUrl}/api/vc/${credentialId}` &&
      response.status() === 200
  );

  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await credentialLoaded;

  await expect(
    page.getByRole('heading', { name: 'Walt.id Portal' })
  ).toBeVisible();
  await page.getByText(credentialId, { exact: true }).click();

  const startButton = page.getByRole('button', { name: /^Start$/ });
  await expect(startButton).toBeInViewport({ ratio: 0.5 });
  await startButton.click();

  await expect(page).toHaveURL(
    new RegExp(`/credentials\\?ids=${credentialId}$`)
  );
  await expect(page.getByText('Credential Configuration')).toBeVisible();
  await expect(page.getByText(credentialId, { exact: true })).toBeVisible();
  await expect(page.getByText('JWT + W3C VC', { exact: true })).toBeVisible();

  const actionRow = page
    .getByRole('button', { name: /^Cancel$/ })
    .locator('xpath=..');
  const issueButton = actionRow.getByRole('button', { name: /^Issue$/ });
  await expect(issueButton).toBeEnabled();

  const issueResponse = page.waitForResponse(
    (response) =>
      response.url() === `${issuerUrl}/openid4vc/jwt/issue` &&
      response.request().method() === 'POST' &&
      response.status() === 200
  );
  await issueButton.click();
  const issueResult = await issueResponse;
  expect(await issueResult.json()).toBe(credentialOfferUrl);

  await expect(page).toHaveURL(
    new RegExp(
      `/offer\\?ids=${credentialId}&authenticationMethod=PRE_AUTHORIZED$`
    )
  );
  await expect(
    page.getByRole('heading', { name: 'Claim Your Credential' })
  ).toBeVisible();
  await expect(
    page.getByRole('button', { name: 'Copy offer URL' })
  ).toBeVisible();

  expect(issueRequestBody).toMatchObject({
    credentialConfigurationId: `${credentialId}_jwt_vc_json`,
    credentialData: {
      ...credential,
      id: expect.any(String),
    },
    mapping,
    authenticationMethod: 'PRE_AUTHORIZED',
  });
});
