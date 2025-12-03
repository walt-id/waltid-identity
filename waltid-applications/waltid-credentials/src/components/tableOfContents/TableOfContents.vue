<template>
    <nav aria-label="Table of contents">
        <ul ref="ul" class="mt-5">
            <ContentQuery v-slot="{ data }" :path="$route.path" find="one">
                <TocElement v-for="element in getTableOfContents(data)" v-if="data" :key="element.id" :toc-element="element" class="table-of-contents-element" />
            </ContentQuery>
        </ul>
    </nav>
</template>

<script lang="ts" setup>
import TocElement from "~/components/tableOfContents/TocElement.vue";

interface Element {
    type: string;
    tag: string;
    props: {
        id: string;
    };
    children: Child[];
}

interface Child {
    type: string;
    value?: string;
}

interface TableOfContentsElement {
    tag: string;
    id: string;
    value: string;
}

function getTableOfContents(data: any) {
    const contentChildren = data?.body?.children;
    const filteredContent = contentChildren?.filter((child: any) => {
        const tagsToFilter = ["h1", "h2", "h3", "h4", "h5", "h6"];
        if (tagsToFilter.includes(child.tag)) {
            if (child.children.length > 0) {
                return true;
            }
        }
    });
    const tableOfContents: TableOfContentsElement[] = filteredContent.map((content: Element) => ({
        tag: content.tag,
        id: content.props.id,
        value: content.children[0].value,
    }));
    return tableOfContents;
}
</script>
