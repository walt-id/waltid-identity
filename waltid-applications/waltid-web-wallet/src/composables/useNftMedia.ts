/**
 * Extracts the NFT media url, or returns a default image url
 * @param art - the NFT art object
 * @returns - the URL of the NFT media
 */
export function useMediaUrl(art: any) {
    const url = ref(convertUrl(art.url));
    const checkReachable = async () => {
        await getXHRPromise(url.value != null ? url.value : "", "HEAD", "Content-Type")
            .then((data) => {
                console.log(`then: ${data}`);
                //TODO: check status code instead of mime-type
                if (mimeType(data as string) == "text") {
                    url.value = art.imageData;
                }
                console.log(`image-data: ${art.imageData}`);
                if (!isNotNullOrEmpty(url.value)) {
                    url.value = "/images/NoImageAvailable.jpg";
                }
                console.log(`result: ${url.value}`);
            })
            .catch((err) => {
                console.log(`catch: ${art.imageData}`);
                if (!isNotNullOrEmpty(art.imageData)) {
                    url.value = "/images/NoImageAvailable.jpg";
                }
                console.log(`result: ${url.value}`);
            });
    };
    checkReachable();
    return { url };
}

/**
 * Computes asynchronously if the content
 * located at the given url has video mime-type
 * @param url the content url
 * @returns reactive 'isVideo' property
 */
export function useIsVideo(url: string) {
    const isVideo = ref(false);
    const computeMimeType = async () => {
        await getXHRPromise(url, "HEAD", "Content-Type")
            .then((data) => {
                const type = mimeType(data as string);
                isVideo.value = type !== undefined && type?.toLowerCase() == "video";
            })
            .catch((err) => {
                // console.error(err)
            });
    };
    computeMimeType();
    return { isVideo };
}

/**
 * Check if an object is not null or an empty string
 * @param value - any value
 * @returns - true if is not null or empty string, false - otherwise
 */
export function isNotNullOrEmpty(value: any) {
    return value != null && value !== "";
}

/**
 * Converts an ipfs gateway url to an http gateway url
 * @param url - the url to be converted
 * @returns - the http gateway url
 */
function convertUrl(url: string | null) {
    let result = url;
    if (url) {
        const regex = "^((ipfs|ipns)://)([1-9A-Za-z]{59}|[1-9A-Za-z]{46})(/[^\\?]*)*(\\?filename=[^\\?]+)?$";
        result = buildUrlFromMatches(url.match(regex));
    }
    return isNotNullOrEmpty(result) ? result : url;
}

/**
 * Builds an http gateway url, given the ipfs gateway url matches (identifier, meta)
 * @param matches - the ipfs url matches
 * @returns - the composed http gateway url
 */
function buildUrlFromMatches(matches: RegExpMatchArray | null) {
    let result = null;
    if (matches != null && matches.length > 3) {
        result = `https://ipfs.io/${matches[2]}/${matches[3]}`;
        if (matches[4] != null) {
            result += matches[4];
        }
        if (matches[5] != null) {
            result += matches[5];
        }
    }
    return result;
}

/**
 * Creates and returns the XHR request Promise for a given URL,
 * using a given method to extract a given header property
 * @param url - the file url to get the http for
 * @param method - the method type (e.g. HEAD)
 * @param header - the header property name
 * @returns - the Promise of the XHR request
 */
function getXHRPromise(url: string, method: string, header = "Content-Type") {
    return new Promise(function (resolve, reject) {
        let xhr = new XMLHttpRequest();
        xhr.open(method, url, true);
        xhr.onload = function () {
            if (this.status >= 200 && this.status < 300) {
                let response = xhr.response;
                if (method.toUpperCase() == "HEAD") {
                    response = xhr.getResponseHeader(header);
                }
                resolve(response);
            } else {
                reject({
                    status: this.status,
                    statusText: xhr.statusText,
                });
            }
        };
        xhr.onerror = function () {
            reject({
                status: this.status,
                statusText: xhr.statusText,
            });
        };
        xhr.send();
    });
}

/**
 * Extracts the mime-type from the http request mimetype header property
 * @param type - the http header mimetype value
 * @returns - the substring without any "/" character, or null if it couldn't be extracted
 */
function mimeType(type: string | null): string | null {
    let result = null;
    if (type) {
        result = type.substring(0, type.indexOf("/"));
    }
    return result;
}
