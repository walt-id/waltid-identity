import axios from 'axios';

export function getStateFromUrl(url: string) {
    try {
        const normalizedUrl = url.replace(/^openid4vp:/, 'https:');
        const parsedUrl = new URL(normalizedUrl);
        return parsedUrl.searchParams.get('state');
    } catch (e) {
        const stateMatch = url.match(/[?&]state=([^&]+)/);
        return stateMatch ? decodeURIComponent(stateMatch[1]) : null;
    }
}

export async function checkVerificationResult(verifierURL: string, sessionId: string): Promise<boolean> {
    const url = `${verifierURL}/openid4vc/session/${encodeURIComponent(sessionId)}`;

    return new Promise((resolve) => {
        const poll = async () => {
            try {
                const response = await axios.get(url, {
                    headers: { 'accept': 'application/json' }
                });

                const data = response.data;

                if (data.verificationResult === true) {
                    return resolve(true);
                }
                else if (data.verificationResult === false) {
                    return resolve(false);
                }

                setTimeout(poll, 1000); // poll again after 1 second
            } catch (error) {
                console.error("Error fetching session:", error);
                return resolve(false);
            }
        };

        poll();
    });
}