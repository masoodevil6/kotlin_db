package gog.my_project.data_base.query.example.v2.queries

import gog.my_project.data_base.query.example.v1.queries.IExampleV1

/** Keeps the existing example runner contract while distinguishing v2 examples. */
interface IExampleV2<T> : IExampleV1<T>
