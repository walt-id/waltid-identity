import { serverQueryContent } from "#content/server";

export default defineEventHandler(async (event) => {
    const name = decodeURIComponent(getRouterParam(event, "name"));

    if (name == "list" || name === undefined) {
        const contentQuery = await serverQueryContent(event).find();
        return contentQuery.filter((page) => page._path?.startsWith("/w3c-credentials/") && page.body?.children.find((elem) => elem.tag === "pre" && elem.props?.language === "json") != undefined).map((elem) => elem.title);
    }

    const contentQuery = await serverQueryContent(event).find();

    const matchedContent = contentQuery.find((content) => {
        return content._path?.startsWith("/w3c-credentials/") && content.title === name;
    });

    if (matchedContent === undefined) {
        const n = contentQuery
            .filter((content) => {
                return content.navigation === undefined;
            })
            .map((content) => {
                return content.title;
            });

        setResponseStatus(event, 400);
        return `error: No credential found named: \"${name}\". Available credentials: ${n.join(", ")}`;
    }

    const code = matchedContent.body?.children.find((elem) => {
        return elem.tag === "pre" && elem.props?.language === "json";
    })?.props?.code;

    return JSON.parse(code);
});
