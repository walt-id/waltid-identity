<template>
    <div class="bg-white shadow">
        <div class="px-4 sm:px-6 lg:mx-auto lg:max-w-6xl lg:px-8">
            <div class="py-6 lg:py-3 md:flex md:items-center md:justify-between lg:border-gray-200">
                <div class="min-w-0 flex-1">
                    <!-- --- -->

                    <div class="flex items-center">
                        <slot name="icon"></slot>
                        <div>
                            <div class="flex items-center">
                                <!-- Title -->
                                <slot name="title"></slot>
                            </div>

                            <!-- --- -->
                        </div>
                    </div>
                </div>
                <div class="mt-6 flex space-x-3 md:ml-4 md:mt-0">
                    <!-- Menu -->
                    <slot name="menu"></slot>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
export default {
    name: "PageHeader",
};
</script>

<style scoped></style>
