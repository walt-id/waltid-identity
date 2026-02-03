import { serverQueryContent } from "#content/server";

export default defineEventHandler(async (event) => {
    const contentQuery = await serverQueryContent(event).find();

    // Include credentials from all directories: W3C, mDoc, and SD-JWT
    const credentialPaths = [
        "/w3c-credentials/",
        "/iso-mdoc-credentials/",
        "/sd-jwt-credentials/"
    ];

    return contentQuery
        .filter((page) => {
            const matchesPath = credentialPaths.some(path => page._path?.startsWith(path));
            const hasJsonBlock = page.body?.children.find(
                (elem) => elem.tag === "pre" && elem.props?.language === "json"
            ) != undefined;
            return matchesPath && hasJsonBlock;
        })
        .map((elem) => elem.title);
});
